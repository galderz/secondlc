To run QueryWith2lcBenchmark/FindWith2lcBenchmark:
-Djub.consumers=CONSOLE,H2 -Djub.db.file=.benchmarks

To add Byteman, add:
-javaagent:/opt/byteman/lib/byteman.jar=listener:true,boot:/opt/byteman/lib/byteman.jar

To run Cluster2lcTest:
-Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1 -ea