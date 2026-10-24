package com.secondhand.config;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
class RepositoryConfigSafetyTest {
 @Test void bootstrapDefinesEveryTableOnlyOnce() throws Exception {
  String sql=new String(new ClassPathResource("sql/init.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
  var matcher=Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)?\\s+`([^`]+)`",Pattern.CASE_INSENSITIVE).matcher(sql);
  var names=new HashSet<String>();
  while(matcher.find()) assertThat(names.add(matcher.group(1))).as("duplicate bootstrap table: %s",matcher.group(1)).isTrue();
  assertThat(names).contains("order","message","meetup_proposal");
 }
 @Test void trackedDatabasePasswordComesFromEnvironment() {
  YamlPropertiesFactoryBean yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yml"));
  assertThat("${DB_PASSWORD:}".equals(yaml.getObject().getProperty("spring.datasource.password"))).as("Tracked config must not contain a database password").isTrue();
 }
}
