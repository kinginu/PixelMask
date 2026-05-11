package com.kinginu.pixelmask

import android.content.Intent
import com.kinginu.pixelmask.Constants.ACTION_AUTH_DATA_BROADCAST
import com.kinginu.pixelmask.Constants.EXTRA_AUTH_DATA
import com.kinginu.pixelmask.Constants.PACKAGE_NAME_GMS
import com.kinginu.pixelmask.Constants.PACKAGE_NAME_GOOGLE_PHOTOS
import com.kinginu.pixelmask.Constants.PREF_DEVICE_TO_SPOOF
import com.kinginu.pixelmask.Constants.PREF_ENABLE_VERBOSE_LOGS
import com.kinginu.pixelmask.Constants.PREF_MODULE_ENABLED
import com.kinginu.pixelmask.Constants.SHARED_PREF_FILE_NAME
import com.kinginu.pixelmask.spoof.DeviceProps
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import android.app.AndroidAppHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.reflect.KClass

@InjectYukiHookWithXposed
class PixelMaskHookEntry : IYukiHookXposedInit {

    override fun onInit() = YukiHookAPI.configs {
        debugLog {
            tag = "PixelMask"
        }
    }

    override fun onHook() = YukiHookAPI.encase {

        // Self app: flip ModuleStatus.hookedFlag so Home renders "Module Active".
        loadApp(name = BuildConfig.APPLICATION_ID) {
            "${BuildConfig.APPLICATION_ID}.utils.ModuleStatus".toClass()
                .resolve()
                .firstField { name = "hookedFlag" }
                .set(true)
        }

        // Google Photos: device-prop spoof + hasSystemFeature override
        loadApp(name = PACKAGE_NAME_GOOGLE_PHOTOS) {
            val sharedPrefs = prefs(SHARED_PREF_FILE_NAME)

            if (!sharedPrefs.getBoolean(PREF_MODULE_ENABLED, true)) return@loadApp

            val verbose = sharedPrefs.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
            val savedName = sharedPrefs.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
            val device = DeviceProps.getDeviceProps(savedName)
                ?: DeviceProps.getDeviceProps(DeviceProps.defaultDeviceName)
                ?: return@loadApp

            val featuresToEnable = DeviceProps.getFeaturesUpToFromDeviceName(device.deviceName)
            val featuresToBlock = DeviceProps.allKnownFeatures - featuresToEnable

            if (verbose) YLog.info("Spoofing $packageName as ${device.deviceName}")

            // Build.* are public static final; XposedHelpers handles the final-modifier bypass.
            // findClass throws (rather than returning null) if android.os.Build isn't on
            // appClassLoader — should never happen, but if it does we want to keep
            // hasSystemFeature spoofing working instead of crashing the whole hook.
            val buildClass = runCatching {
                XposedHelpers.findClass("android.os.Build", appClassLoader)
            }.getOrElse {
                YLog.warn("Failed to resolve android.os.Build, skipping prop spoof", it)
                null
            }
            buildClass?.let {
                device.props.forEach { (key, value) ->
                    XposedHelpers.setStaticObjectField(it, key, value)
                    if (verbose) YLog.debug("DEVICE PROPS: $key - $value")
                }
            }

            // Hook hasSystemFeature(String) and hasSystemFeature(String, int).
            val pmClass = "android.app.ApplicationPackageManager".toClass(appClassLoader)

            fun hookHasSystemFeature(vararg paramTypes: KClass<*>) {
                pmClass.resolve().firstMethod {
                    name = "hasSystemFeature"
                    parameters(*paramTypes)
                }.hook {
                    before {
                        val featureName = args[0]?.toString() ?: return@before
                        when (featureName) {
                            in featuresToEnable -> {
                                result = true
                                if (verbose) YLog.debug("TRUE - feature: $featureName")
                            }
                            in featuresToBlock -> {
                                result = false
                                if (verbose) YLog.debug("FALSE - feature: $featureName")
                            }
                        }
                    }
                }
            }

            hookHasSystemFeature(String::class)
            hookHasSystemFeature(String::class, Int::class)

            // TokenData constructor — Photos's GMS auth wrappers (obfuscated, e.g. Lbkah;, Lbkkw;)
            // ultimately return a com.google.android.gms.auth.TokenData. The wrapper class names
            // shuffle on every Photos release, but TokenData itself is a stable Parcelable type.
            // Hooking its constructor catches every token regardless of which wrapper produced it
            // — including Parcelable deserialization when Photos receives the result from GMS via
            // Binder. Token is the second constructor arg (also field `b`).
            runCatching {
                val tokenDataClass = Class.forName("com.google.android.gms.auth.TokenData", false, appClassLoader)
                val ctors = tokenDataClass.declaredConstructors
                YLog.info("Photos: TokenData ctors = ${ctors.size}, signatures = ${ctors.joinToString { c -> c.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName } }}")
                ctors.forEach { ctor ->
                    runCatching {
                        XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val token = param.args.firstOrNull { it is String && (it as String).isNotBlank() } as? String ?: return
                                if (token.length < 20) return  // skip status strings, error codes
                                YLog.info("captured auth_data from TokenData ctor (${token.length} chars)")
                                postAuthData(token, verbose)
                            }
                        })
                    }.onFailure { YLog.warn("TokenData ctor hook failed", it) }
                }
            }.onFailure { YLog.info("Photos: TokenData class not found (${it.javaClass.simpleName})") }
        }

        // GMS: capture the auth_data request body posted to android.clients.google.com/auth.
        // Only runs if the user has manually scoped this module to com.google.android.gms in
        // LSPosed Manager — otherwise this block never executes.
        loadApp(name = PACKAGE_NAME_GMS) {
            val sharedPrefs = prefs(SHARED_PREF_FILE_NAME)
            if (!sharedPrefs.getBoolean(PREF_MODULE_ENABLED, true)) return@loadApp
            val verbose = sharedPrefs.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)

            YLog.info("GMS auth_data capture hook loaded")

            // Concrete implementations of java.net.HttpURLConnection used by Android's
            // bundled OkHttp. Names vary across Android versions; we try each and ignore
            // misses. Hooking the abstract HttpURLConnection itself does not fire.
            val implCandidates = listOf(
                "com.android.okhttp.internal.huc.HttpURLConnectionImpl",
                "com.android.okhttp.internal.huc.DelegatingHttpsURLConnection",
            )

            implCandidates.forEach { className ->
                runCatching {
                    className.toClass(appClassLoader).resolve()
                        .firstMethod {
                            name = "getOutputStream"
                            parameters()
                        }
                        .hook {
                            after {
                                val conn = instance as? java.net.HttpURLConnection ?: return@after
                                val urlStr = conn.url?.toString() ?: return@after
                                if (!urlStr.contains("android.clients.google.com/auth")) return@after
                                val original = result as? OutputStream ?: return@after
                                if (verbose) YLog.info("wrapping auth OutputStream: $urlStr")
                                result = AuthDataCaptureStream(original, verbose)
                            }
                        }
                }.onFailure {
                    if (verbose) YLog.warn("could not hook $className.getOutputStream", it)
                }
            }
        }
    }
}

/**
 * Wraps an HTTP request OutputStream to passively snapshot the form-urlencoded body.
 * When the body looks like a complete photos.native auth request, hand it off to
 * PixelMask via implicit broadcast so the UI can show it.
 */
private class AuthDataCaptureStream(
    private val delegate: OutputStream,
    private val verbose: Boolean,
) : OutputStream() {

    private val buffer = ByteArrayOutputStream(2048)
    private var saved = false

    override fun write(b: Int) {
        delegate.write(b)
        buffer.write(b)
        maybeSave()
    }

    override fun write(b: ByteArray) {
        delegate.write(b)
        buffer.write(b)
        maybeSave()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        buffer.write(b, off, len)
        maybeSave()
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        try {
            delegate.close()
        } finally {
            maybeSave()
        }
    }

    private fun maybeSave() {
        if (saved) return
        if (buffer.size() < 64) return
        val body = buffer.toString(Charsets.UTF_8.name())
        // Require both the scope marker and the androidId field — this is what gpmc expects.
        if (!body.contains("photos.native")) return
        if (!body.contains("androidId=")) return
        saved = true

        if (verbose) YLog.info("captured auth_data (${body.length} bytes)")
        postAuthData(body, verbose)
    }
}

private fun postAuthData(payload: String, verbose: Boolean) {
    runCatching {
        val app = AndroidAppHelper.currentApplication() ?: return
        // Implicit broadcast — Android 11+ package visibility blocks ContentProvider lookups
        // for callers that don't declare us in <queries>. A runtime-registered exported
        // receiver in PixelMask's process catches this without needing manifest changes
        // in the hooked app.
        val intent = Intent(ACTION_AUTH_DATA_BROADCAST).apply {
            putExtra(EXTRA_AUTH_DATA, payload)
            // FLAG_INCLUDE_STOPPED_PACKAGES still won't auto-start PixelMask, but lets the
            // broadcast reach it if it's been frozen by aggressive OEM battery management.
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        app.sendBroadcast(intent)
        if (verbose) YLog.info("auth_data broadcast sent (${payload.length} chars)")
    }.onFailure {
        if (verbose) YLog.warn("failed to send auth_data broadcast", it)
    }
}
