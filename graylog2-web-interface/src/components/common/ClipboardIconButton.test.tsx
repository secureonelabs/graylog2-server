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
import { render, screen } from 'wrappedTestingLibrary';
import userEvent from '@testing-library/user-event';

import copyToClipboard from 'util/copyToClipboard';

import ClipboardIconButton from './ClipboardIconButton';

jest.mock('util/copyToClipboard', () => jest.fn(() => Promise.resolve()));

describe('ClipboardIconButton', () => {
  it('should copy provided text to clipboard', async () => {
    const text = 'Text to copy';
    render(<ClipboardIconButton text={text} buttonTitle="Click here to copy" />);

    userEvent.click(await screen.findByRole('button', { name: /click here to copy/i }));

    expect(copyToClipboard).toHaveBeenCalledWith(text);

    await screen.findByText('Copied!');
  });
});
