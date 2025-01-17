package dev.nateweisz.bytestore.user.auth

import dev.nateweisz.bytestore.FRONTEND_URL
import dev.nateweisz.bytestore.user.service.UserService
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/auth/")
class AuthController(val userService: UserService) {

    @GetMapping("/login/success")
    fun loginSuccess(authentication: OAuth2AuthenticationToken, @RequestParam("callbackUrl", defaultValue = "account") callback: String,
                     response: HttpServletResponse, session: HttpSession) {
        val user = userService.processOAuthPostLogin(authentication.principal)
        session.setAttribute("user_id", user.githubId)
        response.sendRedirect("${FRONTEND_URL}/$callback")
    }
}