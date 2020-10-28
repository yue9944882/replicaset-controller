package com.github.yue9944882.kubernetes.config;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.informer.SharedInformerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.Executors;

@Component
public class ControllerStarter implements InitializingBean {

    @Resource
    private SharedInformerFactory sharedInformerFactory;

    @Resource(name = "replicaset-reconciler")
    // The name must be the same as the @KubernetesReconciler annotation's value.
    private Controller replicasetController;

    @Override
    public void afterPropertiesSet() throws Exception {
        // Optionally wrap the controller-manager with {@link io.kubernetes.client.extended.leaderelection.LeaderElector}
        // so that the controller works in HA setup.
        // https://github.com/kubernetes-client/java/blob/master/examples/src/main/java/io/kubernetes/client/examples/LeaderElectionExample.java
        ControllerManager controllerManager = new ControllerManager(
                sharedInformerFactory,
                replicasetController
        );
        // Starts the controller-manager in background.
        Executors.newSingleThreadExecutor().submit(controllerManager);
    }
}
