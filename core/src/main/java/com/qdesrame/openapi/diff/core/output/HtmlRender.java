package com.qdesrame.openapi.diff.core.output;

import static com.qdesrame.openapi.diff.core.model.Changed.result;
import static j2html.TagCreator.*;

import com.qdesrame.openapi.diff.core.model.*;
import com.qdesrame.openapi.diff.core.utils.RefPointer;
import com.qdesrame.openapi.diff.core.utils.RefType;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import j2html.TagCreator;
import j2html.tags.ContainerTag;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HtmlRender implements Render {
  static final Logger logger = LoggerFactory.getLogger(HtmlRender.class);
  
  private String title;
  private String linkCss;
  protected static RefPointer<Schema> refPointer = new RefPointer<>(RefType.SCHEMAS);
  protected ChangedOpenApi diff;
  
  private enum ClassType {
      CHANGED,
      INCREASED,
      MISSING,
      NO_CHANGE,
  }

  public HtmlRender() {
    //this("Api Change Log", "http://deepoove.com/swagger-diff/stylesheets/demo.css");
      this("Api Change Log", "swagger_diff.css");
  }

  public HtmlRender(String title, String linkCss) {
    this.title = title;
    this.linkCss = linkCss;
  }

  public String render(ChangedOpenApi diff) {
    this.diff = diff;

    ChangedVersion changedVersion = diff.getChangedVersion();
    ContainerTag ol_changedVersion = ol_changedVersion(changedVersion);
    
    List<Endpoint> newEndpoints = diff.getNewEndpoints();
    ContainerTag ol_newEndpoint = ol_newEndpoint(newEndpoints);

    List<Endpoint> missingEndpoints = diff.getMissingEndpoints();
    ContainerTag ol_missingEndpoint = ol_missingEndpoint(missingEndpoints);

    List<Endpoint> deprecatedEndpoints = diff.getDeprecatedEndpoints();
    ContainerTag ol_deprecatedEndpoint = ol_deprecatedEndpoint(deprecatedEndpoints);

    List<ChangedOperation> changedOperations = diff.getChangedOperations();
    ContainerTag ol_changed = ol_changed(changedOperations);

    return renderHtml(ol_changedVersion, ol_newEndpoint, ol_missingEndpoint, ol_deprecatedEndpoint, ol_changed);
  }

  public String renderHtml(ContainerTag ol_versions, ContainerTag ol_new, ContainerTag ol_miss,
                           ContainerTag ol_deprec, ContainerTag ol_changed) {
    ContainerTag html =
        html()
            .attr("lang", "en")
            .with(
                head()
                    .with(
                        meta().withCharset("utf-8"),
                        title(title),
                        //link().withRel("stylesheet").withHref(linkCss)
                        style(TagCreator.rawHtml(readFileToString(linkCss))).withType("text/css")
                    ),
                body()
                    .with(
                        header().with(h1(title)),
                        div()
                            .withClass("article")
                            .with(
                                div().with(h2("Versions"), hr(), ol_versions),
                                div().with(h2("New methods"), hr(), ol_new),
                                div().with(h2("Deleted methods"), hr(), ol_miss),
                                div().with(h2("Deprecated methods"), hr(), ol_deprec),
                                div().with(h2("Changed methods"), hr(), ol_changed))));

    return document().render() + html.render();
  }

  private ContainerTag ol_changedVersion(ChangedVersion changedVersion) {
      if (null == changedVersion) return ol();
      
      ContainerTag ol = ol();
      ol
          .withText(String.format("Changed from %s to %s",
                                changedVersion.getOldVersion(), changedVersion.getNewVersion()))
          .withClass("version");
      
      return ol;
  }
  private ContainerTag ol_newEndpoint(List<Endpoint> endpoints) {
    if (null == endpoints) return ol();
    ContainerTag ol = ol();
    for (Endpoint endpoint : endpoints) {
      ol.with(
          li_newEndpoint(
              endpoint.getMethod().toString(), endpoint.getPathUrl(), endpoint.getSummary()));
    }
    return ol;
  }

  private ContainerTag li_newEndpoint(String method, String path, String desc) {
    return li()
        .with(span(method).withClass(method))
        .withText(path + " ")
        .with(span(desc).withClass("comment"));
  }

  private ContainerTag ol_missingEndpoint(List<Endpoint> endpoints) {
    if (null == endpoints) return ol();
    ContainerTag ol = ol();
    for (Endpoint endpoint : endpoints) {
      ol.with(
          li_missingEndpoint(
              endpoint.getMethod().toString(), endpoint.getPathUrl(), endpoint.getSummary()));
    }
    return ol;
  }

  private ContainerTag li_missingEndpoint(String method, String path, String desc) {
    return li()
        .with(span(method).withClass(method), del().withText(path))
        .with(span(" " + desc).withClass("comment"));
  }

  private ContainerTag ol_deprecatedEndpoint(List<Endpoint> endpoints) {
    if (null == endpoints) return ol();
    ContainerTag ol = ol();
    for (Endpoint endpoint : endpoints) {
      ol.with(
          li_deprecatedEndpoint(
              endpoint.getMethod().toString(), endpoint.getPathUrl(), endpoint.getSummary()));
    }
    return ol;
  }

  private ContainerTag li_deprecatedEndpoint(String method, String path, String desc) {
    return li()
        .with(span(method).withClass(method), del().withText(path))
        .with(span(" " + desc).withClass("comment"));
  }

  private ContainerTag ol_changed(List<ChangedOperation> changedOperations) {
    if (null == changedOperations) return ol();
    ContainerTag ol = ol();
    for (ChangedOperation changedOperation : changedOperations) {
      String pathUrl = changedOperation.getPathUrl();
      String method = changedOperation.getHttpMethod().toString();
      String desc =
            Optional.ofNullable(changedOperation.getNewOperation().getSummary())
                .orElse("");

      ContainerTag ul_detail = ul().withClass("detail");
      if (result(changedOperation.getParameters()).isDifferent()) {
        ul_detail.with(
            li().with(h3("Parameters")).with(ul_param(changedOperation.getParameters())));
      }
      if (changedOperation.resultRequestBody().isDifferent()) {
        ul_detail.with(
            li().with(h3("Request"))
                .with(ul_request(changedOperation.getRequestBody().getContent())));
      } else {
      }
      if (changedOperation.resultApiResponses().isDifferent()) {
        ul_detail.with(
            li().with(h3("Response"))
                .with(ul_response(changedOperation.getApiResponses())));
      }
      ol.with(
          li().with(span(method).withClass(method))
              .withText(pathUrl + " ")
              .with(span(desc).withClass("comment"))
              .with(ul_detail));
    }
    return ol;
  }

  private ContainerTag ul_response(ChangedApiResponse changedApiResponse) {
    Map<String, ApiResponse> addResponses = changedApiResponse.getIncreased();
    Map<String, ApiResponse> delResponses = changedApiResponse.getMissing();
    Map<String, ChangedResponse> changedResponses = changedApiResponse.getChanged();
    ContainerTag ul = ul().withClass("change response");
    for (String propName : addResponses.keySet()) {
      ul.with(li_addResponse(propName, addResponses.get(propName)));
    }
    for (String propName : delResponses.keySet()) {
      ul.with(li_missingResponse(propName, delResponses.get(propName)));
    }
    for (String propName : changedResponses.keySet()) {
      ul.with(li_changedResponse(propName, changedResponses.get(propName)));
    }
    return ul;
  }

  private ContainerTag li_addResponse(String name, ApiResponse response) {
    return li().withText(String.format("New response : [%s]", name))
        .with(
            span(null == response.getDescription() ? "" : ("//" + response.getDescription()))
                .withClass("comment"));
  }

  private ContainerTag li_missingResponse(String name, ApiResponse response) {
    return li().withText(String.format("Deleted response : [%s]", name))
        .with(
            span(null == response.getDescription() ? "" : ("//" + response.getDescription()))
                .withClass("comment"));
  }

  private ContainerTag li_changedResponse(String name, ChangedResponse response) {
    return li()
        .withText(String.format("Changed response : [%s]", name))
        .with(
            span((null == response.getNewApiResponse()
                        || null == response.getNewApiResponse().getDescription())
                    ? ""
                    : ("//" + response.getNewApiResponse().getDescription()))
                .withClass("comment"))
        .with(ul_request(response.getContent()));
  }

  private ContainerTag ul_request(ChangedContent changedContent) {
    ContainerTag ul = ul().withClass("change request-body");
    if (changedContent != null) {
      for (String propName : changedContent.getIncreased().keySet()) {
        ul.with(li_addRequest(propName, changedContent.getIncreased().get(propName)));
      }
      for (String propName : changedContent.getMissing().keySet()) {
        ul.with(li_missingRequest(propName, changedContent.getMissing().get(propName)));
      }
      for (String propName : changedContent.getChanged().keySet()) {
        ul.with(li_changedRequest(propName, changedContent.getChanged().get(propName)));
      }
    }
    return ul;
  }

  private ContainerTag li_addRequest(String name, MediaType request) {
    return li().withText(String.format("New body: '%s'", name));
  }

  private ContainerTag li_missingRequest(String name, MediaType request) {
    return li().withText(String.format("Deleted body: '%s'", name));
  }

  private ContainerTag li_changedRequest(String name, ChangedMediaType request) {
    ContainerTag innerBlock = ul();
    ContainerTag li =
        li().with(div_changedSchema(request.getSchema()))
            .with(ul().withClass("change xxx")
                      .with(li(String.format("Changed content type: '%s'", name))
                                .with(innerBlock)));
    if (request.isCompatible() || request.isIncompatible()) {
      incompatibilities(innerBlock, request.getSchema());
    }
    return li;
  }

  private ContainerTag div_changedSchema(ChangedSchema schema) {
    ContainerTag div = div();
    ContainerTag schemaTag = h3();
    
    if (schema.isIncompatible()) {
        schemaTag.withText("Schema incompatible").withClass("incompatible");
      }
    else if (schema.isCompatible()) {
        schemaTag.withText("Schema compatible").withClass("compatible");
    }
    else {
        schemaTag.withText("Compatibility Unknown").withClass("compatibility-unknown");
    }
    div.with(schemaTag);
    
    return div;
  }

  private void incompatibilities(final ContainerTag output, final ChangedSchema schema) {
    incompatibilities(output, "", schema);
  }

  private void incompatibilities(
      final ContainerTag output, String propName, final ChangedSchema schema) {
    
    if (schema.getRequired() != null) {
      required(output, ClassType.INCREASED, "New required properties", schema.getRequired().getIncreased());
      required(output, ClassType.MISSING,"Properties deleted from required", schema.getRequired().getMissing());
    }
    if (schema.getItems() != null) {
      items(output, propName, schema.getItems());
    }
    if (schema.isCoreChanged() == DiffResult.INCOMPATIBLE && schema.isChangedType()) {
      String type = type(schema.getOldSchema()) + " -> " + type(schema.getNewSchema());
      property(output, ClassType.CHANGED, propName, "Changed property type", type, "");
    }
    String prefix = propName.isEmpty() ? "" : propName + ".";
    properties(output, ClassType.INCREASED, prefix, "Added property",
               schema.getIncreasedProperties(), schema.getContext());
    properties(output, ClassType.MISSING, prefix, "Deleted property",
               schema.getMissingProperties(), schema.getContext());
    
    listDiffs(output,"Updated enum values", schema.getEnumeration());
    
    schema
        .getChangedProperties()
        .forEach(
            (name, property) -> {
                ContainerTag innerTag = ul();
                output
                    //.withClass("change yyy")
                    .with(li(String.format("%s: %s", "Changed property", prefix + name))
                              .withClass("changed")
                              .with(innerTag));
                incompatibilities(innerTag, prefix + name, property);
            }
        );
  }
  
  private void required(ContainerTag output, ClassType classType, String title, List<String> required) {
      if (required.size() > 0) {
          ContainerTag innerTag = ul();
          output.with(li(title).with(innerTag));
          listItem(innerTag, classType, "", required);
      }
  }
  private void listDiffs(ContainerTag output, String captionCategory, ChangedList<?> listDiff) {
      if (listDiff == null || listDiff.isItemsChanged() == DiffResult.NO_CHANGES) {
          return;
      }
      ContainerTag innerTag = ul();
      output
          .with(li(captionCategory).with(innerTag));
      listItem(innerTag, ClassType.INCREASED, "Added: ", listDiff.getIncreased());
      listItem(innerTag, ClassType.MISSING, "Deleted: ", listDiff.getMissing());
  }
  
  private <T> void listItem(ContainerTag output, ClassType classType, String name, List<T> list) {
      if (!list.isEmpty()) {
          list.forEach(
              value -> {
                  ContainerTag propertyTag = li(String.format("%s%s", name, value));
    
                  switch (classType){
                      case MISSING:
                          propertyTag.withClass("missing");
                          break;
                      case INCREASED:
                          propertyTag.withClass("increased");
                          break;
                      case CHANGED:
                          propertyTag.withClass("changed");
                      default:
                          break;
                  }
                  
                  output.with(propertyTag);
              }
          );
      }
  }

  private void items(ContainerTag output, String propName, ChangedSchema schema) {
    incompatibilities(output, propName + "[n]", schema);
  }

  private void properties(
      ContainerTag output,
      ClassType classType,
      String propPrefix,
      String title,
      Map<String, Schema> properties,
      DiffContext context) {
    if (!properties.isEmpty()) {
      properties.forEach(
          (key, value) -> resolveProperty(output, classType, propPrefix, key, value, title)
      );
    }
  }

  private void resolveProperty(
      ContainerTag output, ClassType classType, String propPrefix, String key, Schema value, String title) {
    try {
      property(output, classType, propPrefix + key, title, resolve(value));
    } catch (Exception e) {
      property(output, classType, propPrefix + key, title, type(value), "");
    }
  }

  protected void property(ContainerTag output, ClassType classType, String name, String title, Schema schema) {
    property(output, classType, name, title, type(schema), schema.getDescription());
  }

  protected void property(ContainerTag output, ClassType classType, String name, String title, String valueType,
                          String description) {
    ContainerTag propertyTag = li(String.format("%s: %s (%s)", title, name, valueType));
    
    if (description != null && !description.isEmpty()) {
      propertyTag.with(span("//" + description).withClass("comment"));
    }

    switch (classType){
        case MISSING:
            propertyTag.withClass("missing");
            break;
        case INCREASED:
            propertyTag.withClass("increased");
            break;
        default:
            break;
    }
    output.with(propertyTag);
  }

  protected Schema resolve(Schema schema) {
    return refPointer.resolveRef(
        diff.getNewSpecOpenApi().getComponents(), schema, schema.get$ref());
  }

  protected String type(Schema schema) {
    String result = "object";
    if (schema == null) {
      result = "no schema";
    } else if (schema instanceof ArraySchema) {
      result = "array";
    } else if (schema.getType() != null) {
      result = schema.getType();
    }
    return result;
  }

  private ContainerTag ul_param(ChangedParameters changedParameters) {
    List<Parameter> addParameters = changedParameters.getIncreased();
    List<Parameter> delParameters = changedParameters.getMissing();
    List<ChangedParameter> changed = changedParameters.getChanged();
    ContainerTag ul = ul().withClass("change param");
    for (Parameter param : addParameters) {
      ul.with(li_addParam(param));
    }
    for (ChangedParameter param : changed) {
      ul.with(li_changedParam(param));
    }
    for (Parameter param : delParameters) {
      ul.with(li_missingParam(param));
    }
    return ul;
  }

  private ContainerTag li_addParam(Parameter param) {
    return li().withText("Add " + param.getName() + " in " + param.getIn())
        .with(
            span(null == param.getDescription() ? "" : ("//" + param.getDescription()))
                .withClass("comment"));
  }

  private ContainerTag li_missingParam(Parameter param) {
    return li().withClass("missing")
        .with(span("Delete"))
        .with(del(param.getName()))
        .with(span("in ").withText(param.getIn()))
        .with(
            span(null == param.getDescription() ? "" : ("//" + param.getDescription()))
                .withClass("comment"));
  }

  private ContainerTag li_deprecatedParam(ChangedParameter param) {
    return li().withClass("missing")
        .with(span("Deprecated"))
        .with(del(param.getName()))
        .with(span("in ").withText(param.getIn()))
        .with(
            span(null == param.getNewParameter().getDescription()
                    ? ""
                    : ("//" + param.getNewParameter().getDescription()))
                .withClass("comment"));
  }

  private ContainerTag li_changedParam(ChangedParameter changeParam) {
    if (changeParam.isDeprecated()) {
      return li_deprecatedParam(changeParam);
    }
    boolean changeRequired = changeParam.isChangeRequired();
    boolean changeDescription = changeParam.getDescription().isDifferent();
    Parameter rightParam = changeParam.getNewParameter();
    Parameter leftParam = changeParam.getNewParameter();
    ContainerTag li = li().withText(changeParam.getName() + " in " + changeParam.getIn());
    if (changeRequired) {
      li.withText(" change into " + (rightParam.getRequired() ? "required" : "not required"));
    }
    if (changeDescription) {
      li.withText(" Notes ")
          .with(del(leftParam.getDescription()).withClass("comment"))
          .withText(" change into ")
          .with(span(rightParam.getDescription()).withClass("comment"));
    }
    return li;
  }
    
    private static String readFileToString(String fileName) {
        logger.debug("Read file: {}", fileName);
        try {
            // https://stackoverflow.com/questions/20389255/reading-a-resource-file-from-within-jar
            // As long as the file.txt resource is available on the classpath then this approach will work
            // the same way regardless of whether the file.txt resource is in a classes/ directory
            // or inside a jar
            InputStream in = HtmlRender.class.getClassLoader().getResourceAsStream(fileName);
            return IOUtils.toString(in);
        } catch (IOException e) {
            logger.error("Impossible to read file {}", fileName, e);
            return "";
        }
    }
}
