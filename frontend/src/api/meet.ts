import { del, get, post, put, type PageResult } from './request'

export interface Meet {
  id?: number
  meetName: string
  edition?: string
  shortName?: string
  host?: string
  organizer?: string
  coOrganizer?: string
  startDate?: string
  endDate?: string
  place?: string
  regStartTime?: string
  regEndTime?: string
  status?: string
  regulationHtml?: string
  isTemplate?: number
  remark?: string
}

export interface MeetGroup {
  id?: number
  meetId?: number
  groupName: string
  groupCode: string
  gender: string
  ageMin?: number
  ageMax?: number
  birthDateStart?: string
  birthDateEnd?: string
  gradeRange?: string
  isTeamScore?: number
  sortNo?: number
  status?: string
  remark?: string
}

export interface Unit {
  id?: number
  meetId?: number
  unitName: string
  unitCode: string
  shortName?: string
  unitType?: string
  numberPrefix?: string
  leader?: string
  coach?: string
  phone?: string
  address?: string
  totalQuota?: number
  status?: string
  remark?: string
}

export interface Venue {
  id?: number
  meetId?: number
  venueName: string
  venueCode: string
  venueType: string
  laneCount?: number
  laneOrderJson?: string
  location?: string
  capacity?: number
  isWindMeasured?: number
  isElectricTiming?: number
  contactName?: string
  contactPhone?: string
  status?: string
  remark?: string
}

export interface MeetQuery {
  page?: number
  size?: number
  keyword?: string
  status?: string
  year?: number
}

// ---------------- 运动会 ----------------
export function pageMeets(params: MeetQuery) {
  return get<PageResult<Meet>>('/meets', params)
}

export function getMeet(id: number) {
  return get<Meet>(`/meets/${id}`)
}

export function createMeet(data: Meet) {
  return post<number>('/meets', data)
}

export function updateMeet(id: number, data: Meet) {
  return put<void>(`/meets/${id}`, data)
}

export function deleteMeet(id: number) {
  return del<void>(`/meets/${id}`)
}

export function updateMeetStatus(id: number, status: string) {
  return put<void>(`/meets/${id}/status`, { status })
}

// ---------------- 组别 ----------------
export function listGroups(params: { meetId?: number; gender?: string; keyword?: string }) {
  return get<PageResult<MeetGroup>>('/groups', { page: 1, size: 200, ...params })
}

export function listGroupsByMeet(meetId: number) {
  return get<MeetGroup[]>(`/meets/${meetId}/groups`)
}

export function getGroup(id: number) {
  return get<MeetGroup>(`/groups/${id}`)
}

export function createGroup(meetId: number, data: MeetGroup) {
  return post<number>(`/meets/${meetId}/groups`, data)
}

export function updateGroup(id: number, data: MeetGroup) {
  return put<void>(`/groups/${id}`, data)
}

export function deleteGroup(id: number) {
  return del<void>(`/groups/${id}`)
}

// ---------------- 单位 ----------------
export function listUnits(params: { meetId?: number; keyword?: string }) {
  return get<PageResult<Unit>>('/units', { page: 1, size: 200, ...params })
}

export function getUnit(id: number) {
  return get<Unit>(`/units/${id}`)
}

export function createUnit(data: Unit) {
  return post<number>('/units', data)
}

export function updateUnit(id: number, data: Unit) {
  return put<void>(`/units/${id}`, data)
}

export function deleteUnit(id: number) {
  return del<void>(`/units/${id}`)
}

// ---------------- 场地 ----------------
export function listVenues(params: { meetId?: number; venueType?: string }) {
  return get<Venue[]>('/venues', params)
}

export function createVenue(data: Venue) {
  return post<number>('/venues', data)
}

export function updateVenue(id: number, data: Venue) {
  return put<void>(`/venues/${id}`, data)
}

export function deleteVenue(id: number) {
  return del<void>(`/venues/${id}`)
}
