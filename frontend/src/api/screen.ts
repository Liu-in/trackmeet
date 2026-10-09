import { del, get, post, put, type PageResult } from './request'

// ==================== 公告 ====================
export interface Announcement {
  id?: number
  meetId?: number
  title: string
  contentHtml?: string
  summary?: string
  /** NOTICE 通知 / RESULT 成绩 / SCHEDULE 日程 / URGENT 紧急 */
  noticeType?: string
  /** PUBLIC 公开 / LEADER 领队可见 / REFEREE 裁判可见 */
  scope?: string
  isTop?: number
  publishTime?: string
  expireTime?: string
  /** DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线 */
  status?: string
  publisherId?: number
  publisherName?: string
  /** 是否推送大屏轮播 */
  screenShow?: number
  /** 大屏停留秒数 */
  screenDuration?: number
  sortNo?: number
  readCount?: number
  remark?: string
}

export interface AnnouncementQuery {
  page?: number
  size?: number
  meetId?: number
  noticeType?: string
  status?: string
  scope?: string
  isTop?: number
  keyword?: string
  beginTime?: string
  endTime?: string
}

export interface AnnouncementPublishDTO {
  status: 'PUBLISHED' | 'OFFLINE' | 'DRAFT'
  screenShow?: number
  screenDuration?: number
}

// ==================== 大屏 ====================
export interface ScreenConfig {
  id?: number
  meetId?: number
  screenName?: string
  /** 轮播间隔（秒） */
  rotateSeconds?: number
  refreshSeconds?: number
  showAnnouncement?: number
  showMedal?: number
  showPoints?: number
  showResult?: number
  showSchedule?: number
  theme?: string
  accessToken?: string
  status?: string
  remark?: string
}

export interface ScreenMeetInfo {
  meetName?: string
  meetShortName?: string
  venue?: string
  startDate?: string
  endDate?: string
  status?: string
}

export interface ScreenMedalRow {
  rankNo?: number
  unitName?: string
  gold?: number
  silver?: number
  bronze?: number
  totalMedal?: number
}

export interface ScreenTeamScoreRow {
  rankNo?: number
  unitName?: string
  totalPoint?: number
}

export interface ScreenResultRow {
  eventName?: string
  roundName?: string
  rankNo?: number
  athleteName?: string
  unitName?: string
  resultRaw?: string
  windSpeed?: number
  /** 是否破纪录候选（后端返回 1/true 时前端高亮） */
  recordCandidate?: number | boolean
  /** 备注（可能包含「破纪录」等文字） */
  remark?: string
}

export interface ScreenBoard {
  meetInfo?: ScreenMeetInfo
  announcements?: Announcement[]
  medals?: ScreenMedalRow[]
  teamScores?: ScreenTeamScoreRow[]
  latestResults?: ScreenResultRow[]
  config?: ScreenConfig
  updatedAt?: string
  message?: string
}

// ==================== 公告接口 ====================
export function pageAnnouncements(params: AnnouncementQuery) {
  return get<PageResult<Announcement>>('/announcements', params)
}

export function getAnnouncement(id: number) {
  return get<Announcement>(`/announcements/${id}`)
}

export function createAnnouncement(data: Announcement) {
  return post<number>('/announcements', data)
}

export function updateAnnouncement(id: number, data: Announcement) {
  return put<void>(`/announcements/${id}`, data)
}

export function deleteAnnouncement(id: number) {
  return del<void>(`/announcements/${id}`)
}

export function publishAnnouncement(id: number, data: AnnouncementPublishDTO) {
  return put<void>(`/announcements/${id}/publish`, data)
}

// ==================== 大屏接口 ====================
export function getScreenConfig(meetId?: number) {
  return get<ScreenConfig>('/screen/config', { meetId })
}

export function createScreenConfig(data: ScreenConfig) {
  return post<number>('/screen/config', data)
}

export function updateScreenConfig(id: number, data: ScreenConfig) {
  return put<void>(`/screen/config/${id}`, data)
}

export function refreshScreenToken() {
  return put<string>('/screen/config/token')
}

/** 大屏数据（需登录，权限 screen:board） */
export function getScreenBoard(meetId?: number) {
  return get<ScreenBoard>('/screen/board', { meetId })
}

/** 大屏数据（匿名 + 令牌，用于外接大屏设备） */
export function getPublicScreenBoard(meetId: number, token: string) {
  return get<ScreenBoard>('/screen/public/board', { meetId, token })
}
