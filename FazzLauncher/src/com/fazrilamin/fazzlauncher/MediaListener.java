package com.fazrilamin.fazzlauncher;

import android.service.notification.NotificationListenerService;

/**
 * Empty notification listener. Android only lets an app read other apps' media sessions
 * (title, artist, play/pause) if it has notification access, granted through this service.
 * It does not read or store any notifications.
 */
public class MediaListener extends NotificationListenerService {
}
