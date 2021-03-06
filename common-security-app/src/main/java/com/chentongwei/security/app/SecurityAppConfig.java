package com.chentongwei.security.app;

import com.chentongwei.security.app.properties.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * APP安全模块核心配置
 *
 * @author chentongwei@bshf360.com 2018-05-25 11:31
 */
@Configuration
@PropertySource("classpath:security.properties")
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAppConfig {

}
