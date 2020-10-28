# Kubernetes ReplicaSet Controller (Re-written in Java)


This is a minimal tutorial example project for writting a kubernetes 
controller/operator in Java. This project re-writes everything the 
kubernetes replicaset-controller in KCM(kube-controller-manager) does.
See more tutorial at TBD.


### Run This Project


```bash
 mvn exec:java -Dexec.mainClass="com.github.yue9944882.kubernetes.Application"
```

### Build Docker Image

```bash
mvn spring-boot:build-image
```

### Minimal Dependency Requirement for writing a Java Controller

```xml
<dependency>
    <groupId>io.kubernetes</groupId>
    <artifactId>client-java-spring-integration</artifactId>
    <version>9.0.0</version>
</dependency>
```


### Run this project in a new KinD cluster

Do the following steps to replace the original kubernetes replicaset-controller 
with our Java replicaset controller:


1. Create a new kind cluster with replicaset-controller disabled:

```bash
kind create cluster --name no-rs --config=no-rs-kind.config.yaml
```

2. Installing the java-replicaset-controller to the new cluster:

(NOTE: the manifest will use the pre-built image `ghcr.io/yue9944882/java-replicaset-controller:1.0-SNAPSHOT`)

```bash
kubectl create -f java-rs-controller.yaml
```

3. Create a new replicaset or whatever else to verify the replicaset workload is working:

```bash
kubectl create -f nginx-rs.yaml
```