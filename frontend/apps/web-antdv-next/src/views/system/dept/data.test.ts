import { expect, it } from 'vitest';

import { buildDeptPayload, parentOptions } from './data';

it('部门父节点排除当前节点和所有后代，保留其他分支', () => {
  const tree = [
    {
      id: '1',
      name: '总公司',
      children: [
        {
          id: '2',
          name: '研发',
          children: [{ id: '3', name: '后端', children: [] }],
        },
        { id: '4', name: '财务', children: [] },
      ],
    },
  ];
  expect(parentOptions(tree, '2')).toEqual([
    {
      value: '1',
      title: '总公司',
      children: [{ value: '4', title: '财务', children: [] }],
    },
  ]);
});

it('部门更新发送完整 DTO，根节点为字符串0，保留显式停用', () => {
  expect(
    buildDeptPayload({
      pid: '',
      name: ' 研发 ',
      sort: 0,
      status: 0,
      remark: '',
    }),
  ).toEqual({ pid: '0', name: '研发', sort: 0, status: 0, remark: '' });
});
