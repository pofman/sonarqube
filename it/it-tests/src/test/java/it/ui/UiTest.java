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
package it.ui;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarScanner;
import it.Category4Suite;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonarqube.ws.client.GetRequest;
import org.sonarqube.ws.client.WsResponse;
import pageobjects.Navigation;
import util.ItUtils;

import static com.codeborne.selenide.Condition.hasText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.WebDriverRunner.url;
import static org.assertj.core.api.Assertions.assertThat;
import static util.ItUtils.projectDir;

public class UiTest {

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = Category4Suite.ORCHESTRATOR;

  @Rule
  public Navigation nav = Navigation.get(ORCHESTRATOR);

  @Test
  public void footer_contains_information() {
    nav.getFooter()
      .should(hasText("Documentation"))
      .should(hasText("SonarSource SA"));
  }

  @Test
  public void footer_contains_version() {
    WsResponse status = ItUtils.newAdminWsClient(ORCHESTRATOR).wsConnector().call(new GetRequest("api/system/status"));
    Map<String, Object> statusMap = ItUtils.jsonToMap(status.content());

    nav.getFooter().should(hasText((String) statusMap.get("version")));
  }

  @Test
  public void many_page_transitions() {
    analyzeSampleProject();

    nav.open("/about");

    // on about page
    $(".about-page-projects-link")
      .shouldBe(visible)
      .shouldHave(text("1"))
      .click();

    // on projects page
    assertThat(url()).contains("/projects");
    $(".project-card-name")
      .shouldBe(visible)
      .shouldHave(text("Sample"))
      .find("a")
      .click();

    // on project dashboard
    assertThat(url()).contains("/dashboard?id=sample");
    $(".overview-quality-gate")
      .shouldBe(visible)
      .shouldHave(text("Passed"));
    $("a[href=\"/component_issues?id=sample#resolved=false|types=CODE_SMELL\"]")
      .shouldBe(visible)
      .shouldHave(text("0"))
      .click();

    // on project issues page
    assertThat(url()).contains("/component_issues?id=sample#resolved=false|types=CODE_SMELL");
    $(".facet.active[data-unresolved]").shouldBe(visible);

    $("#global-navigation").find("a[href=\"/profiles\"]").click();

    // on quality profiles page
    assertThat(url()).contains("/profiles");
    $("table[data-language=xoo]").find("tr[data-name=Basic]").find(".quality-profiles-table-name")
      .shouldBe(visible)
      .shouldHave(text("Basic"))
      .find("a")
      .click();

    // on profile page
    assertThat(url()).contains("/profiles/show?key=xoo-basic");
    $(".quality-profile-inheritance")
      .shouldBe(visible)
      .shouldHave(text("1 active rules"));
  }

  @Test
  public void markdown_help() {
    String tags[] = {"strong", "a", "ul", "ol", "h1", "code", "pre", "blockquote"};

    nav.open("/markdown/help");
    for (String tag : tags) {
      $(tag).shouldBe(visible);
    }
  }

  private static void analyzeSampleProject() {
    ORCHESTRATOR.executeBuild(SonarScanner.create(projectDir("shared/xoo-sample")));
  }
}
