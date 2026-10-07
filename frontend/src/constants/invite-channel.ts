export const inviteChannelConfig = {
  wechatContactName: 'Austerity',
  replyKeywords: ['聪明'],
  qrCodeImageUrl: '/images/wechat-austerity.jpg'
} as const;

export function buildInviteChannelGuide() {
  return [
    '获取 KnowGround 邀请码方式：',
    `1. 扫描注册页二维码，添加微信好友${inviteChannelConfig.wechatContactName}`,
    `2. 发送【${inviteChannelConfig.replyKeywords.join('】、【')}】获取邀请码`,
    '3. 收到邀请码后，回到注册页继续完成注册'
  ].join('\n');
}

export function buildInviteCodeShareMessage(shareLink: string, inviteCode: string) {
  return [
    '知据正在内测，欢迎来体验。',
    `邀请码：${inviteCode}`,
    `注册链接：${shareLink}`,
    '',
    `如果邀请码失效，或者想帮朋友再领取一个，可以扫描注册页二维码添加微信好友【${inviteChannelConfig.wechatContactName}】`,
    `发送【${inviteChannelConfig.replyKeywords.join('】、【')}】获取最新邀请码。`
  ].join('\n');
}
