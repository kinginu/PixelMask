package com.kinginu.pixelmask

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
        }
    }
}
