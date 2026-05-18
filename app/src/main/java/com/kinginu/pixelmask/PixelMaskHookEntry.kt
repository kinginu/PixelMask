package com.kinginu.pixelmask

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import com.kinginu.pixelmask.Constants.ACTION_RESTART_ACK
import com.kinginu.pixelmask.Constants.ACTION_RESTART_PHOTOS
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
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
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

            // Register an in-process kill switch so the user can restart Photos from
            // PixelMask Manager without relying on the system "Force stop" button
            // (which is inert on some OEM skins) or root. The receiver lives only as
            // long as the Photos process; on respawn this hook re-installs it. We
            // attach it from Application.onCreate so we have a valid Context to
            // register against.
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                appClassLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context, intent: Intent) {
                                if (intent.action != ACTION_RESTART_PHOTOS) return
                                if (verbose) YLog.info("Restart request received, killing pid ${Process.myPid()}")
                                // Send ACK back to Manager before dying so it can confirm
                                // the hook is installed and schedule the relaunch. sendBroadcast
                                // queues the intent on AMS via Binder synchronously, so AMS has
                                // the broadcast in hand before killProcess takes effect.
                                val ack = Intent(ACTION_RESTART_ACK).apply {
                                    `package` = BuildConfig.APPLICATION_ID
                                }
                                runCatching { c.sendBroadcast(ack) }
                                Process.killProcess(Process.myPid())
                            }
                        }
                        val filter = IntentFilter(ACTION_RESTART_PHOTOS)
                        // RECEIVER_EXPORTED is required on API 34+ for context-registered
                        // receivers that accept cross-package broadcasts. The constant is
                        // 0x2; using the literal avoids a compile-time API-level gate.
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 33) {
                                app.registerReceiver(receiver, filter, /* RECEIVER_EXPORTED */ 0x2)
                            } else {
                                app.registerReceiver(receiver, filter)
                            }
                        }.onFailure { YLog.warn("Failed to register restart receiver", it) }
                    }
                }
            )
        }
    }
}
