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
// @flow
import React from 'react';
import VersionEvent from './VersionEvent';
import { TooltipsContainer } from '../../../components/mixins/tooltips-mixin';
import type { Event as EventType } from '../../../store/projectActivity/duck';
import { translate } from '../../../helpers/l10n';
import './Event.css';

export default class Event extends React.Component {
  props: {
    event: EventType
  };

  render () {
    const { event } = this.props;

    if (event.category === 'VERSION') {
      return <VersionEvent event={event}/>;
    }

    return (
        <TooltipsContainer>
          <div className="project-activity-event">
            <span className="note">{translate('event.category', event.category)}:</span>
            {' '}
            <strong title={event.description} data-toggle="tooltip">{event.name}</strong>
          </div>
        </TooltipsContainer>
    );
  }
}
