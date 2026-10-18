# 可复现的本机开发镜像；不自动替换当前容器。
FROM elasticsearch:8.19.23

# 仅安装与 ES 版本匹配的 IK 中文分词插件。
RUN bin/elasticsearch-plugin install --batch https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.19.23.zip
