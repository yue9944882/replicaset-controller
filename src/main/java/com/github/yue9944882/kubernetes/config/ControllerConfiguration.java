package com.github.yue9944882.kubernetes.config;

import java.io.IOException;
import java.util.concurrent.Executors;

import com.github.yue9944882.kubernetes.ReplicaSetReconciler;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.spring.extended.controller.annotation.GroupVersionResource;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformer;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformers;
import io.kubernetes.client.spring.extended.controller.factory.KubernetesControllerFactory;
import io.kubernetes.client.util.ClientBuilder;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ControllerConfiguration {

    @Bean
    public ApiClient kubernetesSharedClient() throws IOException {
        return ClientBuilder.defaultClient(); // The apiClient used by informer-factory.
    }

    @Bean
    public SharedInformerFactory sharedInformerFactory() {
        // Registering informer-factory so that the processor {@link io.kubernetes.client.spring.extended.controller.KubernetesInformerFactoryProcessor}
        // automatically provisions the annotated informers.
        return new ControllerSharedInformerFactory();
    }

    @Bean
    public ReplicaSetReconciler replicaSetReconciler() {
        return new ReplicaSetReconciler();
    }

    @Bean("replicaset-controller")
    public KubernetesControllerFactory replicaSetController(SharedInformerFactory sharedInformerFactory, ReplicaSetReconciler rs) {
        return new KubernetesControllerFactory(sharedInformerFactory, rs);
    }

    @Bean
    public CommandLineRunner starter(SharedInformerFactory sharedInformerFactory, @Qualifier("replicaset-controller") Controller replicasetController) {
        return args -> {
            // Optionally wrap the controller-manager with {@link io.kubernetes.client.extended.leaderelection.LeaderElector}
            // so that the controller works in HA setup.
            // https://github.com/kubernetes-client/java/blob/master/examples/src/main/java/io/kubernetes/client/examples/LeaderElectionExample.java
            ControllerManager controllerManager = new ControllerManager(
                    sharedInformerFactory,
                    replicasetController
            );
            // Starts the controller-manager in background.
            Executors.newSingleThreadExecutor().submit(controllerManager);
        };
    }

    @KubernetesInformers({
            @KubernetesInformer( // Adding a pod-informer to the factory for list-watching pod resources
                    apiTypeClass = V1Pod.class,
                    apiListTypeClass = V1PodList.class,
                    groupVersionResource =
                    @GroupVersionResource(
                            apiGroup = "",
                            apiVersion = "v1",
                            resourcePlural = "pods")),
            @KubernetesInformer( // Adding a replicaset-informer to the factory for list-watching replicaset resources
                    apiTypeClass = V1ReplicaSet.class,
                    apiListTypeClass = V1ReplicaSetList.class,
                    groupVersionResource =
                    @GroupVersionResource(
                            apiGroup = "apps",
                            apiVersion = "v1",
                            resourcePlural = "replicasets")),
    })
    class ControllerSharedInformerFactory extends SharedInformerFactory {
    }
}
