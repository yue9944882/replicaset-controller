package com.github.yue9944882.kubernetes.config;

import com.github.yue9944882.kubernetes.ReplicaSetReconciler;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.spring.extended.controller.annotation.GroupVersionResource;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformer;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformers;
import io.kubernetes.client.util.ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ComponentScan("io.kubernetes.client.spring.extended.controller") // Loading informer/reconciler bean processors
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
    public ReplicaSetReconciler replicaSetReconciler(
            ApiClient apiClient,
            Lister<V1Pod> podLister, // Automatically injected by {@link io.kubernetes.client.spring.extended.controller.KubernetesReconcilerProcessor}
            SharedInformer<V1Pod> podSharedInformer, // ditto
            Lister<V1ReplicaSet> rsLister, // ditto
            SharedInformer<V1ReplicaSet> rsSharedInformer// ditto
    ) {
        return new ReplicaSetReconciler(apiClient, podLister, podSharedInformer, rsLister, rsSharedInformer);
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
