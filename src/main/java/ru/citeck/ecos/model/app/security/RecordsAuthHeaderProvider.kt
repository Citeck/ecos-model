package ru.citeck.ecos.model.app.security

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.security.jwt.TokenProvider
import ru.citeck.ecos.records3.spring.web.interceptor.AuthHeaderProvider
import ru.citeck.ecos.records3.spring.web.interceptor.RecordsAuthInterceptor
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Component
class RecordsAuthHeaderProvider(
    private val tokenProvider: TokenProvider,
    private val recordsAuthInterceptor: RecordsAuthInterceptor
) : AuthHeaderProvider {

    companion object {
        private const val REMEMBER_ME = false
    }

    private lateinit var computeToken: Function1<String, String>

    @PostConstruct
    fun init() {
        val validityMs = tokenProvider.getTokenValidityMs(REMEMBER_ME) - 30_000
        computeToken = if (validityMs > 30_000) {
            val loadingCache = CacheBuilder.newBuilder()
                .expireAfterWrite(validityMs, TimeUnit.MILLISECONDS)
                .maximumSize(10)
                .build(CacheLoader.from<String, String> {
                    getSystemAuthHeaderImpl(it ?: "")
                })
            ({ loadingCache.get(it) })
        } else {
            { getSystemAuthHeaderImpl(it) }
        }
        recordsAuthInterceptor.setAuthHeaderProvider(this)
    }

    private fun getSystemAuthHeaderImpl(userName: String): String {
        if (userName.isBlank()) {
            return ""
        }
        val authorities: Collection<GrantedAuthority> = AuthContext.getSystemAuthorities().map {
            SimpleGrantedAuthority(it)
        }

        val principal = User(userName, "", authorities)

        return "Bearer " + tokenProvider.createToken(
            UsernamePasswordAuthenticationToken(principal, null, authorities),
            REMEMBER_ME
        )
    }

    override fun getSystemAuthHeader(userName: String): String? {
        return computeToken.invoke(userName)
    }

    override fun getAuthHeader(userName: String): String? = null
}
