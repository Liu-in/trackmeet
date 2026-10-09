import { get } from './request'

export interface DictItem {
  id?: number
  dictType: string
  itemLabel: string
  itemValue: string
  parentValue?: string
  cssClass?: string
  isDefault?: number
  sortNo?: number
}

/** 按字典类型读取字典项（登录即可访问） */
export function getDictItems(dictType: string) {
  return get<DictItem[]>('/system/dicts/items', { dictType })
}

/** 常用字典类型常量（与初始化数据一致） */
export const DICT_TYPE = {
  eventType: 'event_type',
  trackSubType: 'track_sub_type',
  fieldSubType: 'field_sub_type',
  gender: 'gender',
  meetStatus: 'meet_status',
  registrationStatus: 'registration_status',
  auditStatus: 'audit_status',
  resultStatus: 'result_status',
  foulType: 'foul_type',
  recordType: 'record_type',
  refereeDuty: 'referee_duty',
  certType: 'cert_type',
  roundCode: 'round_code',
  laneAssignMode: 'lane_assign_mode',
  drawMode: 'draw_mode',
  qualificationStatus: 'qualification_status'
} as const
