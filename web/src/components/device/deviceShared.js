import { shanghaiStamp } from '../../status'

export const CHANNEL_OPTIONS = [
  { value: '400', label: '400电话' },
  { value: 'miniprogram', label: '客户小程序' },
  { value: 'provider', label: '服务商扫码' },
  { value: 'wecom', label: '企微群' },
  { value: 'phone', label: '电话受理' }
]

export const CATEGORY_OPTIONS = [
  '无法充电',
  '充电中断',
  '设备离线',
  '计费异常',
  '安装问题',
  '其他'
]

export const URGENCY_OPTIONS = [
  { value: 'high', label: '高' },
  { value: 'normal', label: '中' },
  { value: 'low', label: '低' }
]

export function nowBeijingInput() {
  return shanghaiStamp().replace(' ', 'T')
}

export function createTicketFormState() {
  return {
    channel: '400',
    category: '无法充电',
    urgency: 'normal',
    faultOccurredAt: nowBeijingInput(),
    title: '',
    description: ''
  }
}

export function textOrDash(val) {
  if (val === undefined || val === null) return '—'
  const text = String(val).trim()
  return text || '—'
}
