package com.kinginu.pixelmask

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import com.kinginu.pixelmask.Constants.ACTION_HOOK_REPORT
import com.kinginu.pixelmask.Constants.ACTION_HOOK_STATUS_REQUEST
import com.kinginu.pixelmask.Constants.ACTION_RESTART_ACK
import com.kinginu.pixelmask.Constants.ACTION_RESTART_PHOTOS
import com.kinginu.pixelmask.Constants.EXTRA_HOOK_REPORT
import com.kinginu.pixelmask.Constants.PACKAGE_NAME_GOOGLE_PHOTOS
import com.kinginu.pixelmask.Constants.PREF_DEVICE_TO_SPOOF
import com.kinginu.pixelmask.Constants.PREF_ENABLE_VERBOSE_LOGS
import com.kinginu.pixelmask.Constants.PREF_MODULE_ENABLED
import com.kinginu.pixelmask.Constants.SHARED_PREF_FILE_NAME
import com.kinginu.pixelmask.spoof.DeviceProps
import com.kinginu.pixelmask.utils.HookReport
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

@InjectYukiHookWithXposed
class PixelMaskHookEntry : IYukiHookXposedInit {

    override fun onInit() = YukiHookAPI.configs {
        debugLog {
            tag = "PixelMask"
        }
    }

    override fun onHook() = YukiHookAPI.encase {

        // Self app: flip ModuleStatus.hookedFlag so Home knows the framework loads us.
        loadApp(name = BuildConfig.APPLICATION_ID) {
            "${BuildConfig.APPLICATION_ID}.utils.ModuleStatus".toClass()
                .resolve()
                .firstField { name = "hookedFlag" }
                .set(true)
        }

        // Google Photos: device-prop spoof + hasSystemFeature override
        loadApp(name = PACKAGE_NAME_GOOGLE_PHOTOS) {
            val sharedPrefs = prefs(SHARED_PREF_FILE_NAME)
            val masterEnabled = sharedPrefs.getBoolean(PREF_MODULE_ENABLED, true)
            val verbose = sharedPrefs.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
            val savedName = sharedPrefs.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
            val device = DeviceProps.getDeviceProps(savedName)
                ?: DeviceProps.getDeviceProps(DeviceProps.defaultDeviceName)

            // Results of each step, reported back to the Manager.
            var buildSpoof = HookReport.SKIPPED
            var featureHook = HookReport.SKIPPED
            val featureTrue = AtomicInteger()
            val featureFalse = AtomicInteger()

            if (masterEnabled && device != null) {
                val featuresToEnable = DeviceProps.getFeaturesUpToFromDeviceName(device.deviceName)
                val featuresToBlock = DeviceProps.allKnownFeatures - featuresToEnable

                if (verbose) YLog.info("Spoofing $packageName as ${device.deviceName}")

                // Build.* are public static final. Android 17 refuses reflective writes to
                // static final fields, so this must go through XposedHelpers, whose framework
                // clears the final flag natively. Any failure is caught and reported so
                // hasSystemFeature spoofing still runs.
                buildSpoof = runCatching {
                    val buildClass = XposedHelpers.findClass("android.os.Build", appClassLoader)
                    device.props.forEach { (key, value) ->
                        XposedHelpers.setStaticObjectField(buildClass, key, value)
                        if (verbose) YLog.debug("DEVICE PROPS: $key - $value")
                    }
                    HookReport.OK
                }.getOrElse {
                    YLog.warn("Build prop spoof failed", it)
                    HookReport.describe(it)
                }

                // Hook hasSystemFeature(String) and hasSystemFeature(String, int).
                featureHook = runCatching {
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
                                        featureTrue.incrementAndGet()
                                        if (verbose) YLog.debug("TRUE - feature: $featureName")
                                    }
                                    in featuresToBlock -> {
                                        result = false
                                        featureFalse.incrementAndGet()
                                        if (verbose) YLog.debug("FALSE - feature: $featureName")
                                    }
                                }
                            }
                        }
                    }

                    hookHasSystemFeature(String::class)
                    hookHasSystemFeature(String::class, Int::class)
                    HookReport.OK
                }.getOrElse {
                    YLog.warn("hasSystemFeature hook failed", it)
                    HookReport.describe(it)
                }
            }

            val framework = runCatching {
                with(YukiHookAPI.Status.Executor) { "$name $versionName (API $apiLevel)" }
            }.getOrDefault("unknown")
            val prefsReadable = runCatching { sharedPrefs.isPreferencesAvailable }.getOrDefault(false)
            val currentProcess = processName

            fun buildReport(app: Application): HookReport {
                val pi = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                // Read back through reflection so we report what Photos really sees.
                fun buildField(name: String) =
                    runCatching { Build::class.java.getField(name).get(null)?.toString() }.getOrNull() ?: "?"
                return HookReport(
                    timestamp = System.currentTimeMillis(),
                    processName = currentProcess,
                    moduleVersionCode = BuildConfig.VERSION_CODE,
                    framework = framework,
                    photosVersionName = pi?.versionName ?: "?",
                    photosVersionCode = pi?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1,
                    masterEnabled = masterEnabled,
                    prefsReadable = prefsReadable,
                    target = device?.deviceName ?: "none",
                    buildSpoof = buildSpoof,
                    seenModel = buildField("MODEL"),
                    seenFingerprint = buildField("FINGERPRINT"),
                    featureHook = featureHook,
                    featureTrue = featureTrue.get(),
                    featureFalse = featureFalse.get(),
                )
            }

            fun sendReport(context: Context, app: Application) {
                val intent = Intent(ACTION_HOOK_REPORT).apply {
                    `package` = BuildConfig.APPLICATION_ID
                    // Deliver even if the Manager has never been opened since install
                    // or was force-stopped.
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_HOOK_REPORT, buildReport(app).toJson())
                }
                runCatching { context.sendBroadcast(intent) }
                    .onFailure { YLog.warn("Failed to send hook report", it) }
            }

            // Attach from Application.onCreate so we have a Context. This runs even when
            // the master switch is off, so the Manager still hears from Photos.
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                appClassLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return

                        // Report now, and again once Photos has had time to query features.
                        sendReport(app, app)
                        Handler(Looper.getMainLooper()).postDelayed({ sendReport(app, app) }, 20_000)

                        // In-process kill switch (so the user can restart Photos without the
                        // system "Force stop" button, which is inert on some OEM skins) and
                        // status requests from the Manager. The receiver lives as long as
                        // the Photos process; on respawn this hook re-installs it.
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context, intent: Intent) {
                                when (intent.action) {
                                    ACTION_HOOK_STATUS_REQUEST -> sendReport(c, app)
                                    ACTION_RESTART_PHOTOS -> {
                                        if (verbose) YLog.info("Restart request received, killing pid ${Process.myPid()}")
                                        // Send ACK before dying so the Manager can confirm the
                                        // hook is installed and schedule the relaunch.
                                        // sendBroadcast hands the intent to AMS synchronously,
                                        // so it is queued before killProcess takes effect.
                                        val ack = Intent(ACTION_RESTART_ACK).apply {
                                            `package` = BuildConfig.APPLICATION_ID
                                        }
                                        runCatching { c.sendBroadcast(ack) }
                                        Process.killProcess(Process.myPid())
                                    }
                                }
                            }
                        }
                        val filter = IntentFilter().apply {
                            addAction(ACTION_RESTART_PHOTOS)
                            addAction(ACTION_HOOK_STATUS_REQUEST)
                        }
                        // RECEIVER_EXPORTED is required on API 34+ for context-registered
                        // receivers that accept cross-package broadcasts. The constant is
                        // 0x2; using the literal avoids a compile-time API-level gate.
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 33) {
                                app.registerReceiver(receiver, filter, /* RECEIVER_EXPORTED */ 0x2)
                            } else {
                                app.registerReceiver(receiver, filter)
                            }
                        }.onFailure { YLog.warn("Failed to register Photos receiver", it) }
                    }
                }
            )
        }
    }
}
