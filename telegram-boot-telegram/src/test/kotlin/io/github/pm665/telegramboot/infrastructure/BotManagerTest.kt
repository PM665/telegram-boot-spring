package io.github.pm665.telegramboot.infrastructure

import io.github.pm665.telegramboot.drivers.telegram.BotsFactory
import io.github.pm665.telegramboot.drivers.telegram.OperatorBot
import io.github.pm665.telegramboot.features.messaging.TelegramMessageSender
import io.github.pm665.telegramboot.ports.BotsService.Bot
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.telegram.telegrambots.longpolling.BotSession
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.meta.TelegramUrl
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.mockito.kotlin.any as kAny

class BotManagerTest {
    private val application = mock(TelegramBotsLongPollingApplication::class.java)
    private val botsFactory = mock(BotsFactory::class.java)
    private val messageSender = mock(TelegramMessageSender::class.java)
    private val manager = BotManager(application, botsFactory, messageSender, TelegramUrl.DEFAULT_URL)

    @Test
    fun `start bot exposes telegram registration failure`() {
        `when`(botsFactory.getClient("park_bot")).thenReturn(mock(OperatorBot::class.java))
        doThrow(TelegramApiException("invalid token")).`when`(application).registerBot(eq("token"), any(), any(), any())

        assertThatThrownBy { manager.startBot(Bot("token", "park_bot")) }
            .isInstanceOf(TelegramApiException::class.java)
            .hasMessage("invalid token")
    }

    @Test
    fun `start bot identifies the bot with getMe before registering it for polling`() {
        val operatorBot = mock(OperatorBot::class.java)
        `when`(botsFactory.getClient("park_bot")).thenReturn(operatorBot)
        `when`(operatorBot.execute<User, GetMe>(anyGetMe())).thenReturn(User(1, "Park", true).apply { userName = "park_bot" })
        `when`(application.registerBot(eq("token"), any(), any(), any())).thenReturn(mock(BotSession::class.java))

        manager.startBot(Bot("token", "park_bot"))

        val order = inOrder(operatorBot, application)
        order.verify(operatorBot).execute<User, GetMe>(anyGetMe())
        order.verify(application).registerBot(eq("token"), any(), any(), any())
    }

    @Test
    fun `start bot is aborted when getMe fails`() {
        val operatorBot = mock(OperatorBot::class.java)
        `when`(botsFactory.getClient("park_bot")).thenReturn(operatorBot)
        `when`(operatorBot.execute<User, GetMe>(anyGetMe())).thenThrow(RuntimeException(TelegramApiException("Unauthorized")))

        assertThatThrownBy { manager.startBot(Bot("token", "park_bot")) }
            .hasRootCauseMessage("Unauthorized")
        verify(application, never()).registerBot(any(), any(), any(), any())
    }

    private fun anyGetMe(): GetMe = kAny()
}
