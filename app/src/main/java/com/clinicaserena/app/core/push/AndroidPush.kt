package com.clinicaserena.app.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.clinicaserena.app.ClinicaSerenaApp
import com.clinicaserena.app.MainActivity
import com.clinicaserena.app.R
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [PushTokenSource] sobre Firebase Cloud Messaging. El token nunca se escribe en logs.
 *
 * Usa `getToken`/`deleteToken`, obsoletos desde firebase-messaging 25.1.0 pero vigentes: el registro
 * nuevo por Firebase Installation ID (`register()`/`onRegistered`) es opcional y aún no se confirmó que
 * el `Message.setToken` del backend lo acepte. Migrar cuando el backend lo valide.
 */
@Suppress("DEPRECATION")
class FirebasePushTokenSource : PushTokenSource {
    private val messaging: FirebaseMessaging get() = FirebaseMessaging.getInstance()

    override suspend fun token(): String = messaging.token.await()

    override suspend fun delete() {
        messaging.deleteToken().await()
    }

    override fun setAutoInit(enabled: Boolean) {
        messaging.isAutoInitEnabled = enabled
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}

class AndroidNotificationPermission(private val context: Context) : NotificationPermission {
    override fun granted(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
}

class PrefsPushTokenFlag(context: Context) : PushTokenFlag {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var issued: Boolean
        get() = prefs.getBoolean(KEY_ISSUED, false)
        set(value) = prefs.edit { putBoolean(KEY_ISSUED, value) }

    private companion object {
        const val PREFS_NAME = "push"
        const val KEY_ISSUED = "token_emitido"
    }
}

/** Canal y presentación de los recordatorios. El texto llega de Spring sin datos clínicos. */
object PushNotifications {

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            context.getString(R.string.push_canal_id),
            context.getString(R.string.push_canal_nombre),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.push_canal_descripcion) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Con la app en primer plano FCM no muestra la notificación: se muestra aquí, con el mismo texto. */
    fun show(context: Context, title: String?, body: String?) {
        if (body.isNullOrBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Abre la app en su inicio; no hay endpoint de detalle de cita para el paciente.
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, context.getString(R.string.push_canal_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.push_titulo_por_defecto))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(ContextCompat.getColor(context, R.color.serena_notificacion))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private const val NOTIFICATION_ID = 1001
}

/** Recibe la renovación del token y los mensajes con la app abierta. No registra nada en logs. */
class ClinicaSerenaMessagingService : FirebaseMessagingService() {

    private val push: PushRegistrationManager
        get() = (application as ClinicaSerenaApp).container.pushRegistration

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // El valor no se guarda ni se pasa: el gestor lo vuelve a pedir a FCM al registrar.
        push.onNewToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (!push.remindersExpected()) return
        PushNotifications.show(this, notification.title, notification.body)
    }
}
