1. 看节点
```shell
kubectl get nodes -o wide
kubectl describe node sre-agent-worker
```

2. 看pod
```shell
kubectl -n demo get pods -o wide
kubectl -n demo describe pod <pod-name>
kubectl -n demo logs <pod-name>
```

3. 看事件
```shell
kubectl -n demo get events --sort-by=.lastTimestamp
```

这个命令很重要，后面 Step I 的 K8s evidence provider 会从类似数据里抽：
```text
OOMKilled
FailedScheduling
BackOff
Unhealthy
Killing
Pulled / Created / Started
```