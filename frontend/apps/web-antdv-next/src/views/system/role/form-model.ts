import type { RoleSave, SystemRole } from '#/api/system/role';

export function rolePayload(
  value: RoleSave,
  creating: boolean,
  grant = false,
): RoleSave {
  return {
    name: value.name.trim(),
    remark: value.remark?.trim() ?? '',
    status: value.status,
    ...(creating ? { roleCode: value.roleCode?.trim() } : {}),
    ...(grant ? { permissions: [...(value.permissions ?? [])] } : {}),
  };
}
export function affectsCurrentRoles(
  before: SystemRole,
  after: RoleSave,
  roles: string[],
) {
  return (
    roles.includes(before.roleCode) &&
    ((after.status !== undefined && before.status !== after.status) ||
      (after.permissions !== undefined &&
        [...new Set(before.permissions)].sort().join(',') !==
          [...new Set(after.permissions)].sort().join(',')))
  );
}
