# 启动节点1（broker-id=1000）

cd "E:\java repository\hitch\backend\hitch-notice"
$env:JAVA_HOME="E:\JAVAJDK\jdk-8"
$env:PATH="E:\JAVAJDK\jdk-8\bin;$env:PATH"

Write-Host "启动节点1 (broker-id=1000)..." -ForegroundColor Green
Write-Host "配置文件: application-node1.yml" -ForegroundColor Yellow
Write-Host "日志前缀: [NODE1]" -ForegroundColor Yellow
Write-Host ""

mvn spring-boot:run -D"spring-boot.run.profiles=node1"

