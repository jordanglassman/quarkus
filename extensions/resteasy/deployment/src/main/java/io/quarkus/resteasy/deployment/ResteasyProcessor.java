package io.quarkus.resteasy.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.Application;

import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.resteasy.common.deployment.ResteasyJaxrsProviderBuildItem;
import io.quarkus.resteasy.runtime.ExceptionMapperRecorder;
import io.quarkus.resteasy.runtime.NotFoundExceptionMapper;
import io.quarkus.resteasy.runtime.ResteasyFilter;
import io.quarkus.resteasy.runtime.RolesFilterRegistrar;
import io.quarkus.resteasy.server.common.deployment.ResteasyInjectionReadyBuildItem;
import io.quarkus.resteasy.server.common.deployment.ResteasyServerConfigBuildItem;
import io.quarkus.undertow.deployment.FilterBuildItem;
import io.quarkus.undertow.deployment.ServletBuildItem;
import io.quarkus.undertow.deployment.ServletInitParamBuildItem;
import io.quarkus.undertow.deployment.StaticResourceFilesBuildItem;

/**
 * Processor that finds JAX-RS classes in the deployment
 */
public class ResteasyProcessor {

    private static final String JAVAX_WS_RS_APPLICATION = Application.class.getName();
    private static final String JAX_RS_FILTER_NAME = JAVAX_WS_RS_APPLICATION;
    private static final String JAX_RS_SERVLET_NAME = JAVAX_WS_RS_APPLICATION;

    @BuildStep
    public void jaxrsConfig(Optional<ResteasyServerConfigBuildItem> resteasyServerConfig,
            BuildProducer<ResteasyJaxrsConfigBuildItem> resteasyJaxrsConfig) {
        if (resteasyServerConfig.isPresent()) {
            resteasyJaxrsConfig.produce(new ResteasyJaxrsConfigBuildItem(resteasyServerConfig.get().getPath()));
        }
    }

    @BuildStep
    public void build(
            Optional<ResteasyServerConfigBuildItem> resteasyServerConfig,
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<FilterBuildItem> filter,
            BuildProducer<ServletBuildItem> servlet,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ServletInitParamBuildItem> servletInitParameters,
            ResteasyInjectionReadyBuildItem resteasyInjectionReady) throws Exception {
        feature.produce(new FeatureBuildItem(FeatureBuildItem.RESTEASY));

        if (resteasyServerConfig.isPresent()) {
            String path = resteasyServerConfig.get().getPath();

            //if JAX-RS is installed at the root location we use a filter, otherwise we use a Servlet and take over the whole mapped path
            if (path.equals("/") || path.isEmpty()) {
                filter.produce(FilterBuildItem.builder(JAX_RS_FILTER_NAME, ResteasyFilter.class.getName()).setLoadOnStartup(1)
                        .addFilterServletNameMapping("default", DispatcherType.REQUEST).setAsyncSupported(true)
                        .build());
                reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, ResteasyFilter.class.getName()));
            } else {
                String mappingPath = getMappingPath(path);
                servlet.produce(ServletBuildItem.builder(JAX_RS_SERVLET_NAME, HttpServlet30Dispatcher.class.getName())
                        .setLoadOnStartup(1).addMapping(mappingPath).setAsyncSupported(true).build());
                reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, HttpServlet30Dispatcher.class.getName()));
            }

            for (Entry<String, String> initParameter : resteasyServerConfig.get().getInitParameters().entrySet()) {
                servletInitParameters.produce(new ServletInitParamBuildItem(initParameter.getKey(), initParameter.getValue()));
            }
        }
    }

    /**
     * Install the JAX-RS security provider.
     */
    @BuildStep
    void setupFilter(BuildProducer<ResteasyJaxrsProviderBuildItem> providers) {
        providers.produce(new ResteasyJaxrsProviderBuildItem(RolesFilterRegistrar.class.getName()));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void setupExceptionMapper(BuildProducer<ResteasyJaxrsProviderBuildItem> providers) {
        providers.produce(new ResteasyJaxrsProviderBuildItem(NotFoundExceptionMapper.class.getName()));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(STATIC_INIT)
    void addServletsToExceptionMapper(List<ServletBuildItem> servlets, ExceptionMapperRecorder recorder) {
        recorder.setServlets(servlets.stream().filter(s -> !JAX_RS_SERVLET_NAME.equals(s.getName()))
                .collect(Collectors.toMap(s -> s.getName(), s -> s.getMappings())));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(STATIC_INIT)
    void addStaticResourcesExceptionMapper(StaticResourceFilesBuildItem paths, ExceptionMapperRecorder recorder) {
        //limit to 1000 to not have to many files to display
        Set<String> staticResources = paths.files.stream().filter(this::isHtmlFileName).limit(1000).collect(Collectors.toSet());
        if (staticResources.isEmpty()) {
            staticResources = paths.files.stream().limit(1000).collect(Collectors.toSet());
        }
        recorder.setStaticResource(staticResources);
    }

    private boolean isHtmlFileName(String fileName) {
        return fileName.endsWith(".html") || fileName.endsWith(".htm");
    }

    private String getMappingPath(String path) {
        String mappingPath;
        if (path.endsWith("/")) {
            mappingPath = path + "*";
        } else {
            mappingPath = path + "/*";
        }
        return mappingPath;
    }
}
