package ru.citeck.ecos.model.domain.txn

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.data.sql.repo.find.DbFindPage
import ru.citeck.ecos.data.sql.repo.find.DbFindRes
import ru.citeck.ecos.data.sql.service.DbDataService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.txn.lib.commit.TxnCommitData
import ru.citeck.ecos.txn.lib.commit.repo.AuthoritativeTwoPhaseCommitRepo
import ru.citeck.ecos.txn.lib.commit.repo.TwoPhaseCommitStatus
import ru.citeck.ecos.txn.lib.transaction.TxnId
import java.lang.reflect.Proxy

class AuthoritativeJournalTest {

    private val txnId = TxnId.create("origin", "old-instance")

    @Test
    fun `lookup includes every status and foreign owner without filtering by recovery eligibility`() {

        for (status in TwoPhaseCommitStatus.entries) {
            val entity = EmodelTwoPhaseCommitCoordinatorRepo.TwoPcEntity().also {
                it.txnId = txnId.toString()
                it.status = status.ordinal
                it.ownerApp = "another-app:previous-instance"
                it.data = Json.mapper.toString(TxnCommitData.EMPTY) ?: error("Serialization failed")
            }
            val repo = repo { DbFindRes(listOf(entity), 1) }
            val data = requireNotNull(repo.getRecoveryData(txnId))
            assertThat(data.txnId).isEqualTo(txnId)
            assertThat(data.status).isEqualTo(status)
            assertThat(data.ownerApp).isEqualTo(entity.ownerApp)
            assertThat(data.appsToProcess).isEmpty()
        }
    }

    @Test
    fun `successful empty global lookup is absent`() {

        val repo = repo { DbFindRes.empty() }
        assertThat(repo.getRecoveryData(txnId)).isNull()
    }

    @Test
    fun `read failure propagates instead of becoming absence`() {

        val repo = repo { throw IllegalStateException("database unavailable") }
        assertThatThrownBy { repo.getRecoveryData(txnId) }.hasMessage("database unavailable")
    }

    private fun repo(
        result: () -> DbFindRes<EmodelTwoPhaseCommitCoordinatorRepo.TwoPcEntity>
    ): AuthoritativeTwoPhaseCommitRepo {

        val service = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(DbDataService::class.java)
        ) { _, method, args ->
            check(method.name == "find")
            val arguments = requireNotNull(args)
            check(arguments.size == 3)
            assertThat(Json.mapper.toString(arguments[0]))
                .isEqualTo(Json.mapper.toString(Predicates.eq("__txn_id", txnId.toString())))
            assertThat(arguments[1] as List<*>).isEmpty()
            assertThat(arguments[2]).isEqualTo(DbFindPage.FIRST)
            result()
        }
        val repo = EmodelTwoPhaseCommitCoordinatorRepo()
        val field = repo.javaClass.getDeclaredField("dataService")
        field.isAccessible = true
        field.set(repo, service)
        return repo
    }
}
