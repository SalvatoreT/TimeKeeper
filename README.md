# TimeKeeper

Android app that copies birthday events from your Contacts into the system Calendar. A `WorkManager` job keeps the calendar in sync in the background — both periodically (daily) and reactively (whenever `ContactsContract` changes), so the calendar stays current without needing to open the app.

## Building locally

> [!IMPORTANT]
> `./amper build` requires a `keystore.properties` file at the repo root, **even for debug builds**. If it's missing, Amper 0.10.0 hits a buggy error-reporting code path and the build fails with a misleading stack trace:
>
> ```
> java.lang.NoSuchMethodError: 'void org.gradle.api.problems.ProblemReporter.reporting(org.gradle.api.Action)'
>   at org.jetbrains.amper.android.gradle.AmperAndroidIntegrationProjectPlugin.apply(plugin.kt:144)
> ```
>
> CI generates the real `keystore.properties` from secrets, so it isn't committed (`.gitignore` already excludes it). For local development, drop a placeholder at the repo root — the file just needs to exist; the values aren't read during debug builds:
>
> ```properties
> storeFile=dummy.jks
> storePassword=dummy
> keyAlias=dummy
> keyPassword=dummy
> ```

Then:

```sh
./amper build         # debug APK at build/tasks/_<module>_buildAndroidDebug/
./amper run           # install + launch on a connected device/emulator
```

## UI tests (Maestro)

Flows live under [`maestro/`](maestro/). With the app installed on a running emulator:

```sh
maestro test maestro/sync_now.yaml
```
