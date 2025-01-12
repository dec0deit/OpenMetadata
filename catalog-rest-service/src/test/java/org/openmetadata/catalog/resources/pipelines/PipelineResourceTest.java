/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements. See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.catalog.resources.pipelines;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.http.client.HttpResponseException;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.openmetadata.catalog.Entity;
import org.openmetadata.catalog.api.data.CreatePipeline;
import org.openmetadata.catalog.entity.data.Pipeline;
import org.openmetadata.catalog.exception.CatalogExceptionMessage;
import org.openmetadata.catalog.jdbi3.PipelineRepository.PipelineEntityInterface;
import org.openmetadata.catalog.resources.EntityResourceTest;
import org.openmetadata.catalog.resources.pipelines.PipelineResource.PipelineList;
import org.openmetadata.catalog.type.ChangeDescription;
import org.openmetadata.catalog.type.EntityReference;
import org.openmetadata.catalog.type.TagLabel;
import org.openmetadata.catalog.type.Task;
import org.openmetadata.catalog.util.EntityInterface;
import org.openmetadata.catalog.util.JsonUtils;
import org.openmetadata.catalog.util.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openmetadata.catalog.exception.CatalogExceptionMessage.entityNotFound;
import static org.openmetadata.catalog.util.TestUtils.UpdateType.MINOR_UPDATE;
import static org.openmetadata.catalog.util.TestUtils.adminAuthHeaders;
import static org.openmetadata.catalog.util.TestUtils.assertEntityPagination;
import static org.openmetadata.catalog.util.TestUtils.assertResponse;
import static org.openmetadata.catalog.util.TestUtils.authHeaders;

public class PipelineResourceTest extends EntityResourceTest<Pipeline> {
  private static final Logger LOG = LoggerFactory.getLogger(PipelineResourceTest.class);
  public static List<Task> TASKS;
  public static final TagLabel TIER_1 = new TagLabel().withTagFQN("Tier.Tier1");
  public static final TagLabel USER_ADDRESS_TAG_LABEL = new TagLabel().withTagFQN("User.Address");

  public PipelineResourceTest() {
    super(Pipeline.class, "pipelines", PipelineResource.FIELDS, true);
  }


  @BeforeAll
  public static void setup(TestInfo test) throws HttpResponseException, URISyntaxException {
    EntityResourceTest.setup(test);
    TASKS = new ArrayList<>();
    for (int i=0; i < 3; i++) {
      Task task = new Task().withName("task" + i).withDescription("description")
              .withDisplayName("displayName").withTaskUrl(new URI("http://localhost:0"));
      TASKS.add(task);
    }
  }

  @Override
  public Object createRequest(TestInfo test, String description, String displayName, EntityReference owner) {
    return create(test).withDescription(description).withDisplayName(displayName).withOwner(owner);
  }

  @Override
  public void validateCreatedEntity(Pipeline pipeline, Object request, Map<String, String> authHeaders)
          throws HttpResponseException {
    CreatePipeline createRequest = (CreatePipeline) request;
    validateCommonEntityFields(getEntityInterface(pipeline), createRequest.getDescription(),
            TestUtils.getPrincipal(authHeaders), createRequest.getOwner());
    assertEquals(createRequest.getDisplayName(), pipeline.getDisplayName());
    assertService(createRequest.getService(), pipeline.getService());
    validatePipelineTasks(pipeline, createRequest.getTasks());
    TestUtils.validateTags(pipeline.getFullyQualifiedName(), createRequest.getTags(), pipeline.getTags());
  }

  @Override
  public void validateUpdatedEntity(Pipeline pipeline, Object request, Map<String, String> authHeaders) throws HttpResponseException {
    validateCreatedEntity(pipeline, request, authHeaders);
  }

  @Override
  public void validatePatchedEntity(Pipeline expected, Pipeline updated, Map<String, String> authHeaders) throws HttpResponseException {
    validateCommonEntityFields(getEntityInterface(updated), expected.getDescription(),
            TestUtils.getPrincipal(authHeaders), expected.getOwner());
    assertEquals(expected.getDisplayName(), updated.getDisplayName());
    assertService(expected.getService(), updated.getService());
    validatePipelineTasks(updated, expected.getTasks());
    TestUtils.validateTags(updated.getFullyQualifiedName(), expected.getTags(), updated.getTags());
  }

  @Override
  public EntityInterface<Pipeline> getEntityInterface(Pipeline entity) {
    return new PipelineEntityInterface(entity);
  }

  @Test
  public void post_pipelineWithLongName_400_badRequest(TestInfo test) {
    // Create pipeline with mandatory name field empty
    CreatePipeline create = create(test).withName(TestUtils.LONG_ENTITY_NAME);
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            createPipeline(create, adminAuthHeaders()));
    assertResponse(exception, BAD_REQUEST, "[name size must be between 1 and 64]");
  }

  @Test
  public void post_pipelineWithoutName_400_badRequest(TestInfo test) {
    // Create Pipeline with mandatory name field empty
    CreatePipeline create = create(test).withName("");
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            createPipeline(create, adminAuthHeaders()));
    assertResponse(exception, BAD_REQUEST, "[name size must be between 1 and 64]");
  }

  @Test
  public void post_PipelineAlreadyExists_409_conflict(TestInfo test) throws HttpResponseException {
    CreatePipeline create = create(test);
    createPipeline(create, adminAuthHeaders());
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            createPipeline(create, adminAuthHeaders()));
    assertResponse(exception, CONFLICT, CatalogExceptionMessage.ENTITY_ALREADY_EXISTS);
  }

  @Test
  public void post_validPipelines_as_admin_200_OK(TestInfo test) throws HttpResponseException {
    // Create team with different optional fields
    CreatePipeline create = create(test);
    createAndCheckEntity(create, adminAuthHeaders());

    create.withName(getPipelineName(test, 1)).withDescription("description");
    createAndCheckEntity(create, adminAuthHeaders());
  }

  @Test
  public void post_PipelineWithUserOwner_200_ok(TestInfo test) throws HttpResponseException {
    createAndCheckEntity(create(test).withOwner(USER_OWNER1), adminAuthHeaders());
  }

  @Test
  public void post_PipelineWithTeamOwner_200_ok(TestInfo test) throws HttpResponseException {
    createAndCheckEntity(create(test).withOwner(TEAM_OWNER1).withDisplayName("Pipeline1"), adminAuthHeaders());
  }

  @Test
  public void post_PipelineWithTasks_200_ok(TestInfo test) throws HttpResponseException {
    createAndCheckEntity(create(test).withTasks(TASKS), adminAuthHeaders());
  }

  @Test
  public void post_Pipeline_as_non_admin_401(TestInfo test) {
    CreatePipeline create = create(test);
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            createPipeline(create, authHeaders("test@open-metadata.org")));
    assertResponse(exception, FORBIDDEN, "Principal: CatalogPrincipal{name='test'} is not admin");
  }

  @Test
  public void post_PipelineWithoutRequiredService_4xx(TestInfo test) {
    CreatePipeline create = create(test).withService(null);
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            createPipeline(create, adminAuthHeaders()));
    TestUtils.assertResponseContains(exception, BAD_REQUEST, "service must not be null");
  }

  @Test
  public void post_PipelineWithInvalidOwnerType_4xx(TestInfo test) {
    EntityReference owner = new EntityReference().withId(TEAM1.getId()); /* No owner type is set */

    CreatePipeline create = create(test).withOwner(owner);
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            createPipeline(create, adminAuthHeaders()));
    TestUtils.assertResponseContains(exception, BAD_REQUEST, "type must not be null");
  }

  @Test
  public void post_PipelineWithNonExistentOwner_4xx(TestInfo test) {
    EntityReference owner = new EntityReference().withId(TestUtils.NON_EXISTENT_ENTITY).withType("user");
    CreatePipeline create = create(test).withOwner(owner);
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            createPipeline(create, adminAuthHeaders()));
    assertResponse(exception, NOT_FOUND, entityNotFound("User", TestUtils.NON_EXISTENT_ENTITY));
  }

  @Test
  public void post_PipelineWithDifferentService_200_ok(TestInfo test) throws HttpResponseException {
    EntityReference[] differentServices = {AIRFLOW_REFERENCE, PREFECT_REFERENCE};

    // Create Pipeline for each service and test APIs
    for (EntityReference service : differentServices) {
      createAndCheckEntity(create(test).withService(service), adminAuthHeaders());

      // List Pipelines by filtering on service name and ensure right Pipelines are returned in the response
      PipelineList list = listPipelines("service", service.getName(), adminAuthHeaders());
      for (Pipeline db : list.getData()) {
        assertEquals(service.getName(), db.getService().getName());
      }
    }
  }

  @Test
  public void get_PipelineListWithInvalidLimitOffset_4xx() {
    // Limit must be >= 1 and <= 1000,000
    HttpResponseException exception = assertThrows(HttpResponseException.class, ()
            -> listPipelines(null, null, -1, null, null, adminAuthHeaders()));
    assertResponse(exception, BAD_REQUEST, "[query param limit must be greater than or equal to 1]");

    exception = assertThrows(HttpResponseException.class, ()
            -> listPipelines(null, null, 0, null, null, adminAuthHeaders()));
    assertResponse(exception, BAD_REQUEST, "[query param limit must be greater than or equal to 1]");

    exception = assertThrows(HttpResponseException.class, ()
            -> listPipelines(null, null, 1000001, null, null, adminAuthHeaders()));
    assertResponse(exception, BAD_REQUEST, "[query param limit must be less than or equal to 1000000]");
  }

  @Test
  public void get_PipelineListWithInvalidPaginationCursors_4xx() {
    // Passing both before and after cursors is invalid
    HttpResponseException exception = assertThrows(HttpResponseException.class, ()
            -> listPipelines(null, null, 1, "", "", adminAuthHeaders()));
    assertResponse(exception, BAD_REQUEST, "Only one of before or after query parameter allowed");
  }

  @Test
  public void get_PipelineListWithValidLimitOffset_4xx(TestInfo test) throws HttpResponseException {
    // Create a large number of Pipelines
    int maxPipelines = 40;
    for (int i = 0; i < maxPipelines; i++) {
      createPipeline(create(test, i), adminAuthHeaders());
    }

    // List all Pipelines
    PipelineList allPipelines = listPipelines(null, null, 1000000, null,
            null, adminAuthHeaders());
    int totalRecords = allPipelines.getData().size();
    printPipelines(allPipelines);

    // List limit number Pipelines at a time at various offsets and ensure right results are returned
    for (int limit = 1; limit < maxPipelines; limit++) {
      String after = null;
      String before;
      int pageCount = 0;
      int indexInAllPipelines = 0;
      PipelineList forwardPage;
      PipelineList backwardPage;
      do { // For each limit (or page size) - forward scroll till the end
        LOG.info("Limit {} forward scrollCount {} afterCursor {}", limit, pageCount, after);
        forwardPage = listPipelines(null, null, limit, null, after, adminAuthHeaders());
        printPipelines(forwardPage);
        after = forwardPage.getPaging().getAfter();
        before = forwardPage.getPaging().getBefore();
        assertEntityPagination(allPipelines.getData(), forwardPage, limit, indexInAllPipelines);

        if (pageCount == 0) {  // CASE 0 - First page is being returned. There is no before cursor
          assertNull(before);
        } else {
          // Make sure scrolling back based on before cursor returns the correct result
          backwardPage = listPipelines(null, null, limit, before, null, adminAuthHeaders());
          assertEntityPagination(allPipelines.getData(), backwardPage, limit, (indexInAllPipelines - limit));
        }

        indexInAllPipelines += forwardPage.getData().size();
        pageCount++;
      } while (after != null);

      // We have now reached the last page - test backward scroll till the beginning
      pageCount = 0;
      indexInAllPipelines = totalRecords - limit - forwardPage.getData().size();
      do {
        LOG.info("Limit {} backward scrollCount {} beforeCursor {}", limit, pageCount, before);
        forwardPage = listPipelines(null, null, limit, before, null, adminAuthHeaders());
        printPipelines(forwardPage);
        before = forwardPage.getPaging().getBefore();
        assertEntityPagination(allPipelines.getData(), forwardPage, limit, indexInAllPipelines);
        pageCount++;
        indexInAllPipelines -= forwardPage.getData().size();
      } while (before != null);
    }
  }

  private void printPipelines(PipelineList list) {
    list.getData().forEach(Pipeline -> LOG.info("DB {}", Pipeline.getFullyQualifiedName()));
    LOG.info("before {} after {} ", list.getPaging().getBefore(), list.getPaging().getAfter());
  }

  @Test
  public void put_PipelineUrlUpdate_200(TestInfo test) throws HttpResponseException, URISyntaxException {
    CreatePipeline request = create(test).withService(new EntityReference().withId(AIRFLOW_REFERENCE.getId())
            .withType("pipelineService")).withDescription("description");
    createAndCheckEntity(request, adminAuthHeaders());
    URI pipelineURI = new URI("https://airflow.open-metadata.org/tree?dag_id=airflow_redshift_usage");
    Integer pipelineConcurrency = 110;
    Date startDate = new DateTime("2021-11-13T20:20:39+00:00").toDate();

    // Updating description is ignored when backend already has description
    Pipeline pipeline = updatePipeline(request.withPipelineUrl(pipelineURI)
            .withConcurrency(pipelineConcurrency)
            .withStartDate(startDate), OK, adminAuthHeaders());
    String expectedFQN = AIRFLOW_REFERENCE.getName()+"."+pipeline.getName();
    assertEquals(pipelineURI, pipeline.getPipelineUrl());
    assertEquals(startDate, pipeline.getStartDate());
    assertEquals(pipelineConcurrency, pipeline.getConcurrency());
    assertEquals(expectedFQN, pipeline.getFullyQualifiedName());
  }

  @Test
  public void put_PipelineTasksUpdate_200(TestInfo test) throws IOException {
    CreatePipeline request = create(test).withService(AIRFLOW_REFERENCE).withDescription(null);
    Pipeline pipeline = createAndCheckEntity(request, adminAuthHeaders());

    // Add description and tasks
    ChangeDescription change = getChangeDescription(pipeline.getVersion())
            .withFieldsAdded(Arrays.asList("description", "tasks"));
    pipeline = updateAndCheckEntity(request.withDescription("newDescription").withTasks(TASKS),
            OK, adminAuthHeaders(), MINOR_UPDATE, change);
    validatePipelineTasks(pipeline, TASKS); // TODO clean this up
  }

  @Test
  public void put_AddRemovePipelineTasksUpdate_200(TestInfo test) throws IOException {
    CreatePipeline request = create(test).withService(AIRFLOW_REFERENCE).withDescription(null).withTasks(null);
    Pipeline pipeline = createAndCheckEntity(request, adminAuthHeaders());

    // Add tasks and description
    ChangeDescription change = getChangeDescription(pipeline.getVersion())
            .withFieldsAdded(Arrays.asList("description", "tasks"));
    pipeline = updateAndCheckEntity(request.withDescription("newDescription").withTasks(TASKS),
            OK, adminAuthHeaders(), MINOR_UPDATE, change);

    // TODO update this once task removal is figured out
    // remove a task
    // TASKS.remove(0);
    // change = getChangeDescription(pipeline.getVersion()).withFieldsUpdated(singletonList("tasks"));
    //updateAndCheckEntity(request.withTasks(TASKS), OK, adminAuthHeaders(), MINOR_UPDATE, change);
  }

  @Test
  public void get_nonExistentPipeline_404_notFound() {
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            getPipeline(TestUtils.NON_EXISTENT_ENTITY, adminAuthHeaders()));
    assertResponse(exception, NOT_FOUND,
            entityNotFound(Entity.PIPELINE, TestUtils.NON_EXISTENT_ENTITY));
  }

  @Test
  public void get_PipelineWithDifferentFields_200_OK(TestInfo test) throws HttpResponseException {
    CreatePipeline create = create(test).withDescription("description").withOwner(USER_OWNER1)
            .withService(AIRFLOW_REFERENCE).withTasks(TASKS);
    Pipeline pipeline = createAndCheckEntity(create, adminAuthHeaders());
    validateGetWithDifferentFields(pipeline, false);
  }

  @Test
  public void get_PipelineByNameWithDifferentFields_200_OK(TestInfo test) throws HttpResponseException {
    CreatePipeline create = create(test).withDescription("description").withOwner(USER_OWNER1)
            .withService(AIRFLOW_REFERENCE).withTasks(TASKS);
    Pipeline pipeline = createAndCheckEntity(create, adminAuthHeaders());
    validateGetWithDifferentFields(pipeline, true);
  }

  @Test
  public void patch_PipelineAttributes_200_ok(TestInfo test) throws HttpResponseException, JsonProcessingException {
    // Create Pipeline without description, owner
    Pipeline pipeline = createPipeline(create(test), adminAuthHeaders());
    assertNull(pipeline.getDescription());
    assertNull(pipeline.getOwner());
    assertNotNull(pipeline.getService());

    pipeline = getPipeline(pipeline.getId(), "service,owner", adminAuthHeaders());
    pipeline.getService().setHref(null); // href is readonly and not patchable
    List<TagLabel> pipelineTags = singletonList(TIER_1);

    // Add description, owner and tags when previously they were null
    String origJson = JsonUtils.pojoToJson(pipeline);
    pipeline.withDescription("description").withOwner(TEAM_OWNER1).withTags(pipelineTags);
    ChangeDescription change = getChangeDescription(pipeline.getVersion())
            .withFieldsAdded(Arrays.asList("description", "owner", "tags"));
    pipeline = patchEntityAndCheck(pipeline, origJson, adminAuthHeaders(), MINOR_UPDATE, change);
    pipeline.setOwner(TEAM_OWNER1); // Get rid of href and name returned in the response for owner
    pipeline.setService(AIRFLOW_REFERENCE); // Get rid of href and name returned in the response for service
    pipelineTags = singletonList(USER_ADDRESS_TAG_LABEL);

    // Replace description, tags, owner
    origJson = JsonUtils.pojoToJson(pipeline);
    pipeline.withDescription("description1").withOwner(USER_OWNER1).withTags(pipelineTags);
    change = getChangeDescription(pipeline.getVersion())
            .withFieldsUpdated(Arrays.asList("description", "owner", "tags"));
    pipeline = patchEntityAndCheck(pipeline, origJson, adminAuthHeaders(), MINOR_UPDATE, change);
    pipeline.setOwner(USER_OWNER1); // Get rid of href and name returned in the response for owner
    pipeline.setService(AIRFLOW_REFERENCE); // Get rid of href and name returned in the response for service

    // Remove description, tier, owner
    origJson = JsonUtils.pojoToJson(pipeline);
    pipeline.withDescription(null).withOwner(null).withTags(null);
    change = getChangeDescription(pipeline.getVersion())
            .withFieldsDeleted(Arrays.asList("description", "owner", "tags"));
    patchEntityAndCheck(pipeline, origJson, adminAuthHeaders(), MINOR_UPDATE, change);
  }

  // TODO listing tables test:1
  // TODO Change service?

  @Test
  public void delete_emptyPipeline_200_ok(TestInfo test) throws HttpResponseException {
    Pipeline pipeline = createPipeline(create(test), adminAuthHeaders());
    deletePipeline(pipeline.getId(), adminAuthHeaders());
  }

  @Test
  public void delete_nonEmptyPipeline_4xx() {
    // TODO
  }

  @Test
  public void delete_nonExistentPipeline_404() {
    HttpResponseException exception = assertThrows(HttpResponseException.class, () ->
            deletePipeline(TestUtils.NON_EXISTENT_ENTITY, adminAuthHeaders()));
    assertResponse(exception, NOT_FOUND, entityNotFound(Entity.PIPELINE, TestUtils.NON_EXISTENT_ENTITY));
  }

  public static Pipeline updatePipeline(CreatePipeline create,
                                        Status status,
                                        Map<String, String> authHeaders) throws HttpResponseException {
    return TestUtils.put(getResource("pipelines"),
                          create, Pipeline.class, status, authHeaders);
  }

  public static Pipeline createPipeline(CreatePipeline create,
                                        Map<String, String> authHeaders) throws HttpResponseException {
    return TestUtils.post(getResource("pipelines"), create, Pipeline.class, authHeaders);
  }

  /** Validate returned fields GET .../pipelines/{id}?fields="..." or GET .../pipelines/name/{fqn}?fields="..." */
  private void validateGetWithDifferentFields(Pipeline pipeline, boolean byName) throws HttpResponseException {
    // .../Pipelines?fields=owner
    String fields = "owner";
    pipeline = byName ? getPipelineByName(pipeline.getFullyQualifiedName(), fields, adminAuthHeaders()) :
            getPipeline(pipeline.getId(), fields, adminAuthHeaders());
    assertNotNull(pipeline.getOwner());
    assertNotNull(pipeline.getService()); // We always return the service
    assertNull(pipeline.getTasks());

    // .../Pipelines?fields=owner,service
    fields = "owner,service";
    pipeline = byName ? getPipelineByName(pipeline.getFullyQualifiedName(), fields, adminAuthHeaders()) :
            getPipeline(pipeline.getId(), fields, adminAuthHeaders());
    assertNotNull(pipeline.getOwner());
    assertNotNull(pipeline.getService());
    assertNull(pipeline.getTasks());

    // .../Pipelines?fields=owner,service,tables
    fields = "owner,service,tasks";
    pipeline = byName ? getPipelineByName(pipeline.getFullyQualifiedName(), fields, adminAuthHeaders()) :
            getPipeline(pipeline.getId(), fields, adminAuthHeaders());
    assertNotNull(pipeline.getOwner());
    assertNotNull(pipeline.getService());
    assertNotNull(pipeline.getTasks());
  }

  private static void validatePipelineTasks(Pipeline pipeline, List<Task> expectedTasks) {
    assertEquals(expectedTasks, pipeline.getTasks());
  }

  public static void getPipeline(UUID id, Map<String, String> authHeaders) throws HttpResponseException {
    getPipeline(id, null, authHeaders);
  }

  public static Pipeline getPipeline(UUID id, String fields, Map<String, String> authHeaders)
          throws HttpResponseException {
    WebTarget target = getResource("pipelines/" + id);
    target = fields != null ? target.queryParam("fields", fields): target;
    return TestUtils.get(target, Pipeline.class, authHeaders);
  }

  public static Pipeline getPipelineByName(String fqn, String fields, Map<String, String> authHeaders)
          throws HttpResponseException {
    WebTarget target = getResource("pipelines/name/" + fqn);
    target = fields != null ? target.queryParam("fields", fields): target;
    return TestUtils.get(target, Pipeline.class, authHeaders);
  }

  public static PipelineList listPipelines(String fields, String serviceParam, Map<String, String> authHeaders)
          throws HttpResponseException {
    return listPipelines(fields, serviceParam, null, null, null, authHeaders);
  }

  public static PipelineList listPipelines(String fields, String serviceParam, Integer limitParam,
                                           String before, String after, Map<String, String> authHeaders)
          throws HttpResponseException {
    WebTarget target = getResource("pipelines");
    target = fields != null ? target.queryParam("fields", fields): target;
    target = serviceParam != null ? target.queryParam("service", serviceParam): target;
    target = limitParam != null ? target.queryParam("limit", limitParam): target;
    target = before != null ? target.queryParam("before", before) : target;
    target = after != null ? target.queryParam("after", after) : target;
    return TestUtils.get(target, PipelineList.class, authHeaders);
  }

  private void deletePipeline(UUID id, Map<String, String> authHeaders) throws HttpResponseException {
    TestUtils.delete(getResource("pipelines/" + id), authHeaders);

    // Ensure deleted Pipeline does not exist
    HttpResponseException exception = assertThrows(HttpResponseException.class, () -> getPipeline(id, authHeaders));
    assertResponse(exception, NOT_FOUND, entityNotFound(Entity.PIPELINE, id));
  }

  public static String getPipelineName(TestInfo test) {
    return String.format("pipe_%s", test.getDisplayName());
  }

  public static String getPipelineName(TestInfo test, int index) {
    return String.format("pipe%d_%s", index, test.getDisplayName());
  }

  public static CreatePipeline create(TestInfo test) {
    return new CreatePipeline().withName(getPipelineName(test)).withService(AIRFLOW_REFERENCE);
  }

  public static CreatePipeline create(TestInfo test, int index) {
    return new CreatePipeline().withName(getPipelineName(test, index)).withService(AIRFLOW_REFERENCE);
  }
}
