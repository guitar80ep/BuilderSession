package org.builder.session.jackson.console.config;

import org.builder.session.jackson.client.consumer.ConsumerBackendClient;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableWebMvc
@Configuration
@ComponentScan({"org.builder.session.jackson.console"})
public class SpringConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("/resources/");
    }

    @Bean
    public InternalResourceViewResolver viewResolver() {
        InternalResourceViewResolver viewResolver
                = new InternalResourceViewResolver();
        viewResolver.setViewClass(JstlView.class);
        viewResolver.setPrefix("/WEB-INF/jsp/");
        viewResolver.setSuffix(".jsp");
        return viewResolver;
    }


    @Bean(name = "backendClient")
    @Scope(scopeName = "prototype")
    public ConsumerBackendClient backendClient(ServiceRegistry registry) {
        //Pick a random instance to make calls to.
        ServiceRegistry.Instance instance = registry.resolveHost();
        log.info("Created new backend client to random backend host: " + instance);
        return new ConsumerBackendClient(instance.getAddress(), instance.getPort());
    }

    @Bean(name = "serviceRegistry")
    public ServiceRegistry serviceRegistry() {
        String registryId = System.getenv("CONSUMER_SERVICE_REGISTRY_ID");
        return new ServiceRegistryImpl(registryId);
    }
}