# Work Bridge

Work Bridge is an Android app that mirrors selected work-profile notifications into the personal profile so watches and other personal-profile notification clients can receive them.

## Project Status

Work Bridge is now a standalone project with its own repository, branding, app assets, and release direction.

Current state:

- the current forwarding baseline is working on the target device stack
- the app has a Compose-first UI with dedicated Bridge, Diagnostics, and Settings tabs
- release hardening and public GitHub release prep are still in progress
- Google Play is not part of the current release plan

This project is being optimized for the target device stack already in hand. Broad device compatibility work is not the current goal.

## What Work Bridge Does

- listens for notifications from selected work-profile apps
- reposts them into the personal profile
- preserves tap-through behavior, delete intents, action buttons, and remote input when the source notification supports them
- can optionally cancel the original notification after a successful clone
- keeps diagnostics visible in the shipped app so listener, permission, and background issues are easier to troubleshoot

## Product Direction

Work Bridge is intentionally being kept simple and reliable:

- diagnostics remain available in release builds
- manual package entry remains part of setup
- package names are the canonical app identity in the UI for now
- consistency is preferred over clever but fragile onboarding behavior

## App Structure

### Bridge

- add monitored packages manually
- review recently observed packages
- manage the forwarding list
- surface background-access guidance

### Diagnostics

- check listener access, app notifications, notification permission, and battery optimization state
- use quick recovery actions when forwarding stops working
- inspect recent callback, forwarding, skip, and error activity

### Settings

- enable or pause forwarding
- choose whether the original notification should remain visible after cloning

## Validated Baseline

The following behavior has been validated on the target device stack:

1. A personal-profile `NotificationListenerService` can receive work-profile notifications.
2. Work Bridge can repost forwarded notifications in the personal profile.
3. The watch receives forwarded Work Bridge notifications.
4. Tap-through into the source work-profile app works.
5. Action buttons including `Reply` and `Read` can work in the forwarded copy.
6. Canceling the source notification after cloning can be supported without deleting the clone.

## Build

Requirements:

- JDK 17
- Android SDK with `compileSdk 36`

If your machine defaults to a newer JDK, point Gradle at JDK 17 before building.

Common commands:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## Runtime Notes

- forwarding behavior depends on device policy and work-profile behavior
- background reliability can be affected by OEM battery and background restrictions
- notification action fidelity depends on how the source app constructs its notification
- friendly app-label onboarding is intentionally deferred because work-profile package resolution is not reliable enough yet

## Release Direction

The current public release direction is GitHub-first:

- GitHub repository
- GitHub Releases for APK distribution
- GitHub-first install and support documentation
- donation links in the repository and app

## Repository Notes

- application id: `com.opushkarev.workbridge`
- min SDK: `26`
- target SDK: `36`
- Kotlin: `2.3.20`
- Gradle wrapper: `8.14.4`
