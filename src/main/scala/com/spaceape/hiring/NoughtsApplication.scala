package com.spaceape.hiring;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import com.fasterxml.jackson.databind.ObjectMapper
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object NoughtsApplication {
  def main(args: Array[String]) {
    new NoughtsApplication().run(args:_*)
  }
}

class NoughtsApplication extends Application[NoughtsConfiguration] {
  override def getName() = "noughts"

  override def initialize(bootstrap: Bootstrap[NoughtsConfiguration]) {
    
  }

  override def run(configuration: NoughtsConfiguration, environment: Environment) {
    val resource = new NoughtsResource()
    val objectMapper = new ObjectMapper()
    objectMapper.registerModule(DefaultScalaModule)
    environment.jersey().register(new JacksonMessageBodyProvider(objectMapper, environment.getValidator()));
    environment.jersey().register(resource);
  }

}
