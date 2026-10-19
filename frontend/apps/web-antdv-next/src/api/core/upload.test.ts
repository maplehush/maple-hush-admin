import { beforeEach, describe, expect, it, vi } from 'vitest';

import { upload_file } from './upload';

const mocks = vi.hoisted(() => ({
  upload: vi.fn(),
}));

vi.mock('#/api/request', () => ({
  requestClient: { upload: mocks.upload },
}));

describe('shared image upload', () => {
  beforeEach(() => {
    mocks.upload.mockReset();
  });

  it('uploads through the platform file endpoint and returns its public URL', async () => {
    const file = new File(['image'], 'avatar.png', { type: 'image/png' });
    const response = { uploadId: 'receipt', url: 'https://example.com/a.png' };
    const onSuccess = vi.fn();
    const onProgress = vi.fn();
    mocks.upload.mockResolvedValue(response);

    await upload_file({ file, onProgress, onSuccess });

    expect(mocks.upload).toHaveBeenCalledWith('/files/upload', { file });
    expect(onProgress).toHaveBeenCalledWith({ percent: 100 });
    expect(onSuccess).toHaveBeenCalledWith(response, file);
  });

  it('forwards an upload failure to the component', async () => {
    const file = new File(['image'], 'avatar.png', { type: 'image/png' });
    const onError = vi.fn();
    const cause = new Error('network unavailable');
    mocks.upload.mockRejectedValue(cause);

    await upload_file({ file, onError });

    expect(onError).toHaveBeenCalledWith(cause);
  });
});
