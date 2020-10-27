package com.github.yue9944882.kubernetes.config;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
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
    private Controller replicasetController;

    @Override
    public void afterPropertiesSet() throws Exception {
        ControllerManager controllerManager = new ControllerManager(
                sharedInformerFactory,
                replicasetController
        );
        Executors.newSingleThreadExecutor().submit(controllerManager);
    }
}
