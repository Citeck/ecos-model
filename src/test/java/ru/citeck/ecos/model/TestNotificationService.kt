package ru.citeck.ecos.model

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.notifications.lib.service.NotificationService
import java.util.*

@Configuration
class TestNotificationService {

    @Bean
    fun notificationService(): NotificationService {
        return NotificationServiceTestImpl()
    }

    class NotificationServiceTestImpl : NotificationService {
        private val inMemNotificationStorage = Collections.synchronizedList(ArrayList<Notification>())

        override fun sendSync(notification: Notification): SendNotificationResult {
            inMemNotificationStorage.add(notification)
            return SendNotificationResult("ok", "ok")
        }

        override fun send(notification: Notification) {
            inMemNotificationStorage.add(notification)
        }

        fun getNotifications(): List<Notification> {
            return ArrayList(inMemNotificationStorage)
        }

        fun cleanNotificationStorage() {
            inMemNotificationStorage.clear()
        }
    }
}
