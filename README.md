# Kubernetes ReplicaSet Controller (Re-written in Java)


This is a minimal tutorial example project for writting a kubernetes 
controller/operator in Java. This project re-writes everything the 
kubernetes replicaset-controller in KCM(kube-controller-manager) does.
See more tutorial at TBD.


### Run This Project


```bash
 mvn exec:java -Dexec.mainClass="com.github.yue9944882.kubernetes.Application"
```


### Minimal Dependency Requirement

```xml
<dependency>
    <groupId>io.kubernetes</groupId>
    <artifactId>client-java-spring-integration</artifactId>
    <version>9.0.0</version>
</dependency>
```
