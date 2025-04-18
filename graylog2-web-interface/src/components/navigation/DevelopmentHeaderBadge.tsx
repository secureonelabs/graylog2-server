/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
import React from 'react';

import AppConfig from 'util/AppConfig';
import { Badge } from 'components/bootstrap';

type Props = {
  smallScreen?: boolean;
};

const DevelopmentHeaderBadge = ({ smallScreen = false }: Props) => {
  const smallScreenClass = smallScreen ? 'small-scrn-badge' : '';

  return AppConfig.gl2DevMode() ? (
    <Badge className={`${smallScreenClass} dev-badge`} bsStyle="danger">
      {AppConfig.isCloud() ? String.fromCharCode(0x26c8) : ''} DEV
    </Badge>
  ) : null;
};

export default DevelopmentHeaderBadge;
