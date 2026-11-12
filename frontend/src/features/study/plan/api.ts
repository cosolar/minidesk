import { http } from '@/core/http/client'

export interface StudyPlanNode {
  id: string
  parentId: string | null
  level: number
  title: string
  targetHours: number
  progress: number
  taskTotal: number
  taskDone: number
  startDate: string | null
  endDate: string | null
  status: string
  children: StudyPlanNode[]
}

export const studyPlanApi = {
  forest() {
    return http.get<StudyPlanNode[]>('/study/plans')
  },
  create(payload: {
    title: string
    level: number
    parentId?: string | null
    targetHours?: number
    startDate?: string
    endDate?: string
  }) {
    return http.post<StudyPlanNode>('/study/plans', payload)
  },
  update(id: string, payload: { title?: string; targetHours?: number; status?: string }) {
    return http.put<void>(`/study/plans/${id}`, payload)
  },
  remove(id: string) {
    return http.delete<void>(`/study/plans/${id}`)
  },
}
