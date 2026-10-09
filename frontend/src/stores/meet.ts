import { defineStore } from 'pinia'
import { listGroupsByMeet, listUnits, pageMeets, type Meet, type MeetGroup, type Unit } from '@/api/meet'
import { MEET_ID_KEY } from '@/api/request'

interface MeetState {
  meets: Meet[]
  currentMeetId?: number
  currentMeet?: Meet
  groups: MeetGroup[]
  units: Unit[]
  loaded: boolean
}

export const useMeetStore = defineStore('meet', {
  state: (): MeetState => ({
    meets: [],
    currentMeetId: localStorage.getItem(MEET_ID_KEY) ? Number(localStorage.getItem(MEET_ID_KEY)) : undefined,
    currentMeet: undefined,
    groups: [],
    units: [],
    loaded: false
  }),

  getters: {
    groupOptions: (state) => state.groups.map((item) => ({ label: item.groupName, value: item.id })),
    unitOptions: (state) => state.units.map((item) => ({ label: item.unitName, value: item.id }))
  },

  actions: {
    async loadMeets() {
      const page = await pageMeets({ page: 1, size: 100 })
      this.meets = page.records || []
      if (!this.currentMeetId && this.meets.length > 0) {
        await this.setCurrentMeet(this.meets[0].id as number)
      } else if (this.currentMeetId) {
        this.currentMeet = this.meets.find((item) => item.id === this.currentMeetId)
      }
      this.loaded = true
      return this.meets
    },

    async setCurrentMeet(meetId: number) {
      this.currentMeetId = meetId
      localStorage.setItem(MEET_ID_KEY, String(meetId))
      this.currentMeet = this.meets.find((item) => item.id === meetId)
      if (!this.currentMeet) {
        const page = await pageMeets({ page: 1, size: 100 })
        this.meets = page.records || []
        this.currentMeet = this.meets.find((item) => item.id === meetId)
      }
      await this.loadGroupsAndUnits()
    },

    async loadGroupsAndUnits() {
      const meetId = this.currentMeetId
      if (!meetId) {
        this.groups = []
        this.units = []
        return
      }
      const [groups, units] = await Promise.all([
        listGroupsByMeet(meetId),
        listUnits({ meetId }).then((page) => page.records || [])
      ])
      this.groups = groups || []
      this.units = units || []
    }
  }
})
