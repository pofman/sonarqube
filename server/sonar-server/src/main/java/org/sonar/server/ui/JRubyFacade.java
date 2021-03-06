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
package org.sonar.server.ui;

import java.sql.Connection;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.platform.NewUserHandler;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.ResourceType;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.server.authentication.IdentityProvider;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.web.Footer;
import org.sonar.api.web.Page;
import org.sonar.api.web.RubyRailsWebservice;
import org.sonar.api.web.Widget;
import org.sonar.core.platform.ComponentContainer;
import org.sonar.core.platform.PluginInfo;
import org.sonar.core.platform.PluginRepository;
import org.sonar.core.timemachine.Periods;
import org.sonar.db.Database;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.property.PropertiesDao;
import org.sonar.db.property.PropertyDto;
import org.sonar.db.rule.RuleRepositoryDto;
import org.sonar.db.version.DatabaseVersion;
import org.sonar.process.ProcessProperties;
import org.sonar.server.authentication.IdentityProviderRepository;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.platform.PersistentSettings;
import org.sonar.server.platform.Platform;
import org.sonar.server.platform.db.migration.DatabaseMigrationState;
import org.sonar.server.platform.ws.UpgradesAction;
import org.sonar.server.user.NewUserNotifier;

import static com.google.common.collect.Lists.newArrayList;
import static org.sonar.server.platform.db.migration.DatabaseMigrationState.Status;

public final class JRubyFacade {

  private static final JRubyFacade SINGLETON = new JRubyFacade();

  public static JRubyFacade getInstance() {
    return SINGLETON;
  }

  <T> T get(Class<T> componentType) {
    return getContainer().getComponentByType(componentType);
  }

  public Collection<ResourceType> getResourceTypesForFilter() {
    return get(ResourceTypes.class).getAll(ResourceTypes.AVAILABLE_FOR_FILTERS);
  }

  public Collection<ResourceType> getResourceTypes() {
    return get(ResourceTypes.class).getAllOrdered();
  }

  public Collection<ResourceType> getResourceRootTypes() {
    return get(ResourceTypes.class).getRoots();
  }

  public ResourceType getResourceType(String qualifier) {
    return get(ResourceTypes.class).get(qualifier);
  }

  public List<String> getQualifiersWithProperty(final String propertyKey) {
    List<String> qualifiers = newArrayList();
    for (ResourceType type : getResourceTypes()) {
      if (type.getBooleanProperty(propertyKey) == Boolean.TRUE) {
        qualifiers.add(type.getQualifier());
      }
    }
    return qualifiers;
  }

  public Boolean getResourceTypeBooleanProperty(String resourceTypeQualifier, String resourceTypeProperty) {
    ResourceType resourceType = getResourceType(resourceTypeQualifier);
    if (resourceType != null) {
      return resourceType.getBooleanProperty(resourceTypeProperty);
    }
    return null;
  }

  public Collection<String> getResourceLeavesQualifiers(String qualifier) {
    return get(ResourceTypes.class).getLeavesQualifiers(qualifier);
  }

  public Collection<String> getResourceChildrenQualifiers(String qualifier) {
    return get(ResourceTypes.class).getChildrenQualifiers(qualifier);
  }

  // PLUGINS ------------------------------------------------------------------
  public PropertyDefinitions getPropertyDefinitions() {
    return get(PropertyDefinitions.class);
  }

  /**
   * Used for WS api/updatecenter/installed_plugins, to be replaced by api/plugins/installed.
   */
  public Collection<PluginInfo> getPluginInfos() {
    return get(PluginRepository.class).getPluginInfos();
  }

  public List<ViewProxy<Widget>> getWidgets() {
    return get(Views.class).getWidgets();
  }

  public ViewProxy<Widget> getWidget(String id) {
    return get(Views.class).getWidget(id);
  }

  public ViewProxy<Page> getPage(String id) {
    return get(Views.class).getPage(id);
  }

  public Collection<RubyRailsWebservice> getRubyRailsWebservices() {
    return getContainer().getComponentsByType(RubyRailsWebservice.class);
  }

  public Collection<Language> getLanguages() {
    return getContainer().getComponentsByType(Language.class);
  }

  public Database getDatabase() {
    return get(Database.class);
  }

  public boolean isDbUptodate() {
    return getContainer().getComponentByType(DatabaseVersion.class).getStatus() == DatabaseVersion.Status.UP_TO_DATE;
  }

  /* PROFILES CONSOLE : RULES AND METRIC THRESHOLDS */

  /**
   * @deprecated in 4.2
   */
  @Deprecated
  @CheckForNull
  public RuleRepositoryDto getRuleRepository(String repositoryKey) {
    DbClient dbClient = get(DbClient.class);
    try (DbSession dbSession = dbClient.openSession(false)) {
      return dbClient.ruleRepositoryDao().selectByKey(dbSession, repositoryKey).orElse(null);
    }
  }

  public Collection<RuleRepositoryDto> getRuleRepositories() {
    DbClient dbClient = get(DbClient.class);
    try (DbSession dbSession = dbClient.openSession(false)) {
      return dbClient.ruleRepositoryDao().selectAll(dbSession);
    }
  }

  public List<Footer> getWebFooters() {
    return getContainer().getComponentsByType(Footer.class);
  }

  public void saveProperty(String key, @Nullable Long componentId, @Nullable Long userId, @Nullable String value) {
    if (componentId == null && userId == null) {
      get(PersistentSettings.class).saveProperty(key, value);
    } else {
      DbClient dbClient = get(DbClient.class);
      PropertiesDao propertiesDao = dbClient.propertiesDao();

      try (DbSession dbSession = dbClient.openSession(false)) {
        if (value == null) {
          propertiesDao.delete(dbSession, new PropertyDto().setKey(key).setResourceId(componentId).setUserId(userId));
        } else {
          propertiesDao.saveProperty(dbSession, new PropertyDto().setKey(key).setResourceId(componentId).setUserId(userId).setValue(value));
        }
        dbSession.commit();
      }
    }
  }

  public Settings getSettings() {
    return get(Settings.class);
  }

  public String getConfigurationValue(String key) {
    return get(Settings.class).getString(key);
  }

  public Connection getConnection() {
    try {
      return get(Database.class).getDataSource().getConnection();
    } catch (Exception e) {
      /* activerecord does not correctly manage exceptions when connection can not be opened. */
      return null;
    }
  }

  public Object getCoreComponentByClassname(String className) {
    try {
      return get(Class.forName(className));
    } catch (ClassNotFoundException e) {
      Loggers.get(getClass()).error("Component not found: " + className, e);
      return null;
    }
  }

  public Object getComponentByClassname(String pluginKey, String className) {
    Plugin plugin = get(PluginRepository.class).getPluginInstance(pluginKey);
    try {
      Class componentClass = plugin.getClass().getClassLoader().loadClass(className);
      return get(componentClass);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(String.format("Class [%s] not found in plugin [%s]", className, pluginKey), e);
    }
  }

  private JRubyI18n getJRubyI18n() {
    return get(JRubyI18n.class);
  }

  public String getMessage(String rubyLocale, String key, String defaultValue, Object... parameters) {
    return getJRubyI18n().message(rubyLocale, key, defaultValue, parameters);
  }

  /*
   * /!\ Used by Views
   */
  public void deleteResourceTree(String projectKey) {
    try {
      get(ComponentCleanerService.class).delete(projectKey);
    } catch (RuntimeException e) {
      Loggers.get(JRubyFacade.class).error("Fail to delete resource with key: " + projectKey, e);
      throw e;
    }
  }

  public void logError(String message) {
    Loggers.get(getClass()).error(message);
  }

  public String getServerHome() {
    return get(Settings.class).getString(ProcessProperties.PATH_HOME);
  }

  public ComponentContainer getContainer() {
    return Platform.getInstance().getContainer();
  }

  // USERS
  public void onNewUser(Map<String, String> fields) {
    NewUserNotifier notifier = get(NewUserNotifier.class);
    // notifier is null when creating the administrator in the migration script 011.
    if (notifier != null) {
      notifier.onNewUser(NewUserHandler.Context.builder()
        .setLogin(fields.get("login"))
        .setName(fields.get("name"))
        .setEmail(fields.get("email"))
        .build());
    }
  }

  public String getPeriodLabel(int periodIndex) {
    return get(Periods.class).label(periodIndex);
  }

  public String getPeriodLabel(String mode, String param, Date date) {
    return get(Periods.class).label(mode, param, date);
  }

  public String getPeriodLabel(String mode, String param, String date) {
    return get(Periods.class).label(mode, param, date);
  }

  public String getPeriodAbbreviation(int periodIndex) {
    return get(Periods.class).abbreviation(periodIndex);
  }

  /**
   * Checks whether the SQ instance is up and running (ie. not in safemode and with an up-to-date database).
   * <p>
   * This method duplicates most of the logic code written in {@link UpgradesAction}
   * class. There is no need to refactor code to avoid this duplication since this method is only used by RoR code
   * which will soon be replaced by pure JS code based on the {@link UpgradesAction}
   * WebService.
   * </p>
   */
  public boolean isSonarAccessAllowed() {
    ComponentContainer container = Platform.getInstance().getContainer();
    DatabaseMigrationState databaseMigrationState = container.getComponentByType(DatabaseMigrationState.class);
    Status status = databaseMigrationState.getStatus();
    if (status == Status.RUNNING
      || status == Status.FAILED) {
      return false;
    }
    if (status == Status.SUCCEEDED) {
      return true;
    }

    DatabaseVersion databaseVersion = container.getComponentByType(DatabaseVersion.class);
    Integer currentVersion = databaseVersion.getVersion();
    if (currentVersion == null) {
      throw new IllegalStateException("Version can not be retrieved from Database. Database is either blank or corrupted");
    }
    if (currentVersion >= DatabaseVersion.LAST_VERSION) {
      return true;
    }

    Database database = container.getComponentByType(Database.class);
    return !database.getDialect().supportsMigration();
  }

  /**
   * Used by Developer Cockpit
   */
  public void indexComponent(String componentUuid) {
    DbClient dbClient = get(DbClient.class);
    try (DbSession dbSession = dbClient.openSession(false)) {
      dbClient.componentIndexDao().indexResource(dbSession, componentUuid);
    }
  }

  public List<IdentityProvider> getIdentityProviders() {
    return get(IdentityProviderRepository.class).getAllEnabledAndSorted();
  }

}
