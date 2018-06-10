package com.chentongwei.security.app.jwt.filter;

import com.alibaba.fastjson.JSON;
import com.chentongwei.security.app.enums.JwtRedisEnum;
import com.chentongwei.security.app.enums.JwtUrlEnum;
import com.chentongwei.security.app.jwt.util.JwtTokenUtil;
import com.chentongwei.security.app.properties.SecurityProperties;
import com.chentongwei.security.core.authorize.CoreAuthorizeConfigProvider;
import com.chentongwei.security.core.response.ResponseEntity;
import com.chentongwei.security.validate.authorize.ValidateAuthorizeConfigProvider;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * JWT过滤器
 *
 * @author chentongwei@bshf360.com 2018-06-08 14:31
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private final Logger logger = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private CoreAuthorizeConfigProvider coreAuthorizeConfigProvider;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        logger.info("请求路径：【{}】，请求方式为：【{}】", request.getRequestURI(), request.getMethod());
        // 排除路径，并且如果是options请求是cors跨域预请求，设置allow对应头信息
        String[] permitUrls = getPermitUrls();
        for (int i = 0, length = permitUrls.length; i < length; i ++) {
            if (antPathMatcher.match(permitUrls[i], request.getRequestURI())
                    || Objects.equals(RequestMethod.OPTIONS.toString(), request.getMethod())) {
                logger.info("jwt不拦截此路径：【{}】，请求方式为：【{}】", request.getRequestURI(), request.getMethod());
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 获取Authorization
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.isBlank(authHeader) || ! authHeader.startsWith("Bearer ")) {
            logger.error("Authorization的开头不是Bearer，Authorization===>【{}】", authHeader);
            responseEntity(response, HttpStatus.UNAUTHORIZED.value(), "暂无权限！");
            return;
        }

        // 截取token
        String authToken = authHeader.substring("Bearer ".length());

        // 判断token是否失效
        if (jwtTokenUtil.isTokenExpired(authToken)) {
            logger.info("token已过期！");
            responseEntity(response, HttpStatus.UNAUTHORIZED.value(), "token已过期！");
            return;
        }

        String randomKey = jwtTokenUtil.getMd5KeyFromToken(authToken);
        String username = jwtTokenUtil.getUsernameFromToken(authToken);

        // 验证token是否合法
        if (StringUtils.isBlank(username) || StringUtils.isBlank(randomKey)) {
            logger.info("username【{}】或randomKey【{}】可能为null！", username, randomKey);
            responseEntity(response, HttpStatus.UNAUTHORIZED.value(), "暂无权限！");
            return;
        }

        // 验证token是否存在（过期了也会消失）
        Object token = redisTemplate.opsForValue().get(JwtRedisEnum.getKey(username, randomKey));
        if (Objects.isNull(token)) {
            logger.info("Redis里没查到key【{}】对应的value！", JwtRedisEnum.getKey( username, randomKey));
            responseEntity(response, HttpStatus.UNAUTHORIZED.value(), "token已过期！");
            return;
        }

        // 判断传来的token和存到redis的token是否一致
        if (! Objects.equals(token.toString(), authToken)) {
            logger.error("前端传来的token【{}】和redis里的token【{}】不一致！", authToken, token.toString());
            responseEntity(response, HttpStatus.UNAUTHORIZED.value(), "暂无权限！");
            return;
        }

        // token过期时间
        long tokenExpireTime = jwtTokenUtil.getExpirationDateFromToken(authToken).getTime();
        // token还剩余多少时间过期
        long surplusExpireTime = (tokenExpireTime - System.currentTimeMillis()) / 1000;
        logger.info("surplusExpireTime:" + surplusExpireTime);

        /*
         * 退出登录不刷新token，因为假设退出登录操作，刷新token了，这样清除的是旧的token，相当于根本没退出成功
         */
        if (! Objects.equals(request.getRequestURL(), JwtUrlEnum.LOGOUT.url())) {
            // token过期时间小于等于多少秒，自动刷新token
            if (surplusExpireTime <= securityProperties.getJwt().getAutoRefreshTokenExpiration()) {
                // 1.删除之前的token
                redisTemplate.delete(JwtRedisEnum.getKey(username, randomKey));

                //2.重新生成token
                // 重新生成randomKey，放到header以及redis
                randomKey = jwtTokenUtil.getRandomKey();
                // 重新生成token，放到header以及redis
                authToken = jwtTokenUtil.refreshToken(authToken, randomKey);
                response.setHeader("Authorization", "Bearer " + authToken);
                response.setHeader("randomKey", randomKey);
                redisTemplate.opsForValue().set(JwtRedisEnum.getKey(username, randomKey),
                        authToken,
                        securityProperties.getJwt().getExpiration(),
                        TimeUnit.SECONDS);
                logger.info("重新生成token【{}】和randomKey【{}】", authToken, randomKey);
            }
        }

        // TODO JWTUserDetails的问题
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // 避免了每次查询db，直接从jwt取
            UserDetails userDetails = jwtTokenUtil.getUserFromToken(authToken);

            if (jwtTokenUtil.validateToken(authToken, userDetails)) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                logger.info("authenticated user " + username + ", setting security context");
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String[] getPermitUrls() {

        /** 核心模块相关的URL */
        String[] corePermitUrls = coreAuthorizeConfigProvider.getPermitUrls();
        /** 验证模块相关的URL */
        String[] validatePermitUrls = ValidateAuthorizeConfigProvider.getPermitUrls();

        /** 返回的数组 */
        return (String[])ArrayUtils.addAll(corePermitUrls, validatePermitUrls);
    }

    private void responseEntity(HttpServletResponse response, Integer status, String msg) {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        try {
            response.getWriter().write(
                    JSON.toJSONString(
                            new ResponseEntity(status, msg).data(null)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
