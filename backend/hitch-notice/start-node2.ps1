# 启动节点2（broker-id=1001）

cd "E:\java repository\hitch\backend\hitch-notice"
$env:JAVA_HOME="E:\JAVAJDK\jdk-8"
$env:PATH="E:\JAVAJDK\jdk-8\bin;$env:PATH"

Write-Host "启动节点2 (broker-id=1001)..." -ForegroundColor Green
Write-Host "配置文件: application-node2.yml" -ForegroundColor Yellow
Write-Host "日志前缀: [NODE2]" -ForegroundColor Yellow
Write-Host ""

mvn spring-boot:run -D"spring-boot.run.profiles=node2"

