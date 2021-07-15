# java-dapr-adaptor

An client adaptor layer for Dapr 

## v0.1.0 TODO List
- [x] 读取dapr的enable环境变量决定是否开启代理
- [ ] grpc服务适配, 考虑扩展性, 兼容java生态多种rpc框架, eg. thrift, dubbo, hsf 
- [ ] 多注册中心支持(consul, eureka, redis, nacos, zk, etcd3..)
- [x] grpc多版本支持, 允许多版本共存 如: package proto.v1.UserService
- [ ] 流量管理
- [x] 做个springboot-starter, 自动扫描注册

