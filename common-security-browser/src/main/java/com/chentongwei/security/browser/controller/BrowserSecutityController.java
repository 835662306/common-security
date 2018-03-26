package com.chentongwei.security.browser.controller;

import com.chentongwei.security.browser.entity.SimpleResponse;
import com.chentongwei.security.core.constant.SecurityConstant;
import com.chentongwei.security.core.enums.LoginType;
import com.chentongwei.security.core.properties.SecurityProperties;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * 登录控制器
 *
 * @author chentongwei@bshf360.com 2018-03-26 11:14
 */
@RestController
public class BrowserSecutityController {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private RequestCache requestCache = new HttpSessionRequestCache();

    private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Autowired
    private SecurityProperties securityProperties;

    @GetMapping(SecurityConstant.DEFAULT_UNAUTHENTICATION_URL)
    public SimpleResponse requireAuthentication(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 跳转
        if (Objects.equals(securityProperties.getBrowser().getLoginType(), LoginType.REDIRECT)) {
            logger.info("跳转到了【{}】", securityProperties.getBrowser().getLoginPage());
            redirectStrategy.sendRedirect(request, response, securityProperties.getBrowser().getLoginPage());
        } else {
            // JSON
            logger.info("返回的是状态码401的JSON");
            return new SimpleResponse(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(), null);
        }
        return null;
    }
}