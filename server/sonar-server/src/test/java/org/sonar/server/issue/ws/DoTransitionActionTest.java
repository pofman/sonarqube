/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.ws;

import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.MapSettings;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDbTester;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDbTester;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.issue.IssueFieldsSetter;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.issue.IssueUpdater;
import org.sonar.server.issue.ServerIssueStorage;
import org.sonar.server.issue.TransitionService;
import org.sonar.server.issue.index.IssueIndexDefinition;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.workflow.FunctionExecutor;
import org.sonar.server.issue.workflow.IssueWorkflow;
import org.sonar.server.notification.NotificationManager;
import org.sonar.server.rule.DefaultRuleFinder;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsAction;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sonar.api.issue.Issue.STATUS_CONFIRMED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.web.UserRole.CODEVIEWER;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.rule.RuleTesting.newRuleDto;
import static org.sonar.server.issue.IssueTesting.newDto;

public class DoTransitionActionTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  @Rule
  public EsTester esTester = new EsTester(new IssueIndexDefinition(new MapSettings()));

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private DbClient dbClient = dbTester.getDbClient();

  private RuleDbTester ruleDbTester = new RuleDbTester(dbTester);
  private IssueDbTester issueDbTester = new IssueDbTester(dbTester);
  private ComponentDbTester componentDbTester = new ComponentDbTester(dbTester);

  private IssueFieldsSetter updater = new IssueFieldsSetter();
  private IssueWorkflow workflow = new IssueWorkflow(new FunctionExecutor(updater), updater);
  private TransitionService transitionService = new TransitionService(userSession, workflow);
  private OperationResponseWriter responseWriter = mock(OperationResponseWriter.class);
  private IssueUpdater issueUpdater = new IssueUpdater(dbClient,
    new ServerIssueStorage(new DefaultRuleFinder(dbClient), dbClient, new IssueIndexer(System2.INSTANCE, dbClient, esTester.client())), mock(NotificationManager.class));

  private WsAction underTest = new DoTransitionAction(dbClient, userSession, new IssueFinder(dbClient, userSession), issueUpdater, transitionService, responseWriter);
  private WsActionTester tester = new WsActionTester(underTest);

  @Before
  public void setUp() throws Exception {
    workflow.start();
  }

  @Test
  public void do_transition() throws Exception {
    IssueDto issueDto = issueDbTester.insertIssue(newIssue().setStatus(STATUS_OPEN).setResolution(null));
    userSession.login("john").addProjectUuidPermissions(USER, issueDto.getProjectUuid());

    call(issueDto.getKey(), "confirm");

    verify(responseWriter).write(eq(issueDto.getKey()), any(Request.class), any(Response.class));
    IssueDto issueReloaded = dbClient.issueDao().selectByKey(dbTester.getSession(), issueDto.getKey()).get();
    assertThat(issueReloaded.getStatus()).isEqualTo(STATUS_CONFIRMED);
  }

  @Test
  public void fail_if_issue_does_not_exist() throws Exception {
    userSession.login("john");

    expectedException.expect(NotFoundException.class);
    call("UNKNOWN", "confirm");
  }

  @Test
  public void fail_if_no_issue_param() throws Exception {
    userSession.login("john");

    expectedException.expect(IllegalArgumentException.class);
    call(null, "confirm");
  }

  @Test
  public void fail_if_no_transition_param() throws Exception {
    IssueDto issueDto = issueDbTester.insertIssue(newIssue().setStatus(STATUS_OPEN).setResolution(null));
    userSession.login("john").addProjectUuidPermissions(USER, issueDto.getProjectUuid());

    expectedException.expect(IllegalArgumentException.class);
    call(issueDto.getKey(), null);
  }

  @Test
  public void fail_if_not_enough_permission_to_access_issue() throws Exception {
    IssueDto issueDto = issueDbTester.insertIssue(newIssue().setStatus(STATUS_OPEN).setResolution(null));
    userSession.login("john").addProjectUuidPermissions(CODEVIEWER, issueDto.getProjectUuid());

    expectedException.expect(ForbiddenException.class);
    call(issueDto.getKey(), "confirm");
  }

  @Test
  public void fail_if_not_enough_permission_to_apply_transition() throws Exception {
    IssueDto issueDto = issueDbTester.insertIssue(newIssue().setStatus(STATUS_OPEN).setResolution(null));
    userSession.login("john").addProjectUuidPermissions(USER, issueDto.getProjectUuid());

    // False-positive transition is requiring issue admin permission
    expectedException.expect(ForbiddenException.class);
    call(issueDto.getKey(), "falsepositive");
  }

  @Test
  public void fail_if_not_authenticated() throws Exception {
    expectedException.expect(UnauthorizedException.class);
    call("ISSUE_KEY", "confirm");
  }

  private TestResponse call(@Nullable String issueKey, @Nullable String transition) {
    TestRequest request = tester.newRequest();
    if (issueKey != null) {
      request.setParam("issue", issueKey);
    }
    if (transition != null) {
      request.setParam("transition", transition);
    }
    return request.execute();
  }

  private IssueDto newIssue() {
    RuleDto rule = ruleDbTester.insertRule(newRuleDto());
    ComponentDto project = componentDbTester.insertProject();
    ComponentDto file = componentDbTester.insertComponent(newFileDto(project));
    return newDto(rule, file, project);
  }

}
