import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { Modal } from '../common/Modal';
import { api } from '../../services/api';
import type { Channel } from '../../types';

type Phase = 'loading' | 'waiting' | 'scanned' | 'confirmed' | 'expired' | 'error';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_FAILURES = 5;

export function WechatQrModal({ channel, onClose }: { channel: Channel; onClose: () => void }) {
  const [phase, setPhase] = useState<Phase>('loading');
  const [wechatUrl, setWechatUrl] = useState<string | null>(null);   // 原始微信链接
  const [qrcodeId, setQrcodeId] = useState<string | null>(null);     // 轮询用的二维码标识
  const [errMsg, setErrMsg] = useState<string>('');
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pollFailuresRef = useRef(0);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cancelledRef = useRef(false);

  const stopPoll = () => {
    if (pollTimerRef.current) { clearTimeout(pollTimerRef.current); pollTimerRef.current = null; }
    cancelledRef.current = true;
  };

  const generateQrOnCanvas = async (text: string) => {
    if (!canvasRef.current) return;
    try {
      await QRCode.toCanvas(canvasRef.current, text, {
        width: 240,
        margin: 2,
        color: { dark: '#000000ff', light: '#ffffffff' },
      });
    } catch (e) {
      // canvas 生成失败时回退到旧方案（极少发生）
      console.warn('[WechatQr] Canvas QR generation failed, falling back to URL:', e);
    }
  };

  const doPoll = async () => {
    if (cancelledRef.current || !qrcodeId) return;
    try {
      const res = await api.channels.wechat.qrcodeStatus(channel.id, channel.token || '', qrcodeId);
      // iLink 返回 { data: { status, ret, bot_token, ... }, errcode, ret, errmsg }
      // 后端包一层 { success, data: <iLink raw> }
      const payload: any = (res as any)?.data?.data ?? (res as any)?.data;
      const status: string = payload?.status;
      const ret: number = payload?.ret;

      // 成功收到响应，重置失败计数
      pollFailuresRef.current = 0;

      // confirmed 检查必须先于 scaned — iLink 确认后 status 仍为 scaned + ret=0
      if (status === 'confirmed' || (status === 'scaned' && ret === 0)) {
        setPhase('confirmed');
        setTimeout(onClose, 1200);
        return; // 停止轮询
      } else if (status === 'scaned' || status === 'scanned') {
        setPhase('scanned');
      } else if (status === 'expired' || status === 'canceled') {
        setPhase('expired');
        return; // 停止轮询
      }
      // 其他情况（waiting / undefined）继续轮询

      // 安排下一次轮询（递归 setTimeout 而非 setInterval，防止并发堆积）
      if (!cancelledRef.current) {
        pollTimerRef.current = setTimeout(doPoll, POLL_INTERVAL_MS);
      }
    } catch (e) {
      pollFailuresRef.current++;
      console.warn("[WechatQr] poll error (" + pollFailuresRef.current + "/" + MAX_POLL_FAILURES + ")", e);
      if (pollFailuresRef.current >= MAX_POLL_FAILURES) {
        setPhase('error');
        setErrMsg('轮询状态失败次数过多，请刷新二维码重试');
      } else if (!cancelledRef.current) {
        // 失败后重试，带指数退避
        const backoff = Math.min(1000 * Math.pow(2, pollFailuresRef.current), 10000);
        pollTimerRef.current = setTimeout(doPoll, backoff);
      }
    }
  };

  const startPoll = () => {
    stopPoll();
    cancelledRef.current = false;
    pollFailuresRef.current = 0;
    // 第一次轮询延迟 1 秒后开始（给用户一点看 QR 码的时间）
    pollTimerRef.current = setTimeout(doPoll, 1000);
  };

  const fetchQr = async () => {
    setPhase('loading');
    setWechatUrl(null);
    setQrcodeId(null);
    setErrMsg('');
    pollFailuresRef.current = 0;

    try {
      const res = await api.channels.wechat.qrcode(channel.id, channel.token || '');
      const inner = (res as any)?.data?.data ?? (res as any)?.data;
      const wcUrl: string = inner?.qrcode_img_content;
      const qrcode: string = inner?.qrcode;
      if (!res.success || !wcUrl || !qrcode) {
        setPhase('error');
        setErrMsg(`字段缺失: ${JSON.stringify(inner)}`);
        return;
      }
      setWechatUrl(wcUrl);
      setQrcodeId(qrcode);

      // 生成本地 QR 码到 canvas（不依赖 Google Charts，国内可用）
      await generateQrOnCanvas(wcUrl);

      setPhase('waiting');
      // 轮询由 useEffect([qrcodeId]) 自动启动
    } catch (e: any) {
      setPhase('error');
      setErrMsg(String(e?.message ?? e));
    }
  };

  const handleRetry = () => {
    fetchQr();
  };

  useEffect(() => {
    fetchQr();
    return stopPoll;
  }, []); // eslint-disable-line

  // 当 qrcodeId 变更时重新启动轮询
  useEffect(() => {
    if (qrcodeId) {
      startPoll();
    }
    return stopPoll;
  }, [qrcodeId]); // eslint-disable-line

  return (
    <Modal isOpen onClose={onClose} title={`扫码登录 · ${channel.name}`} size="sm">
      <div className="flex flex-col items-center gap-4 py-2">
        {/* QR area */}
        <div className="relative w-60 h-60 rounded-lg bg-white flex items-center justify-center overflow-hidden">
          {phase === 'loading' && <span className="text-gray-400 text-sm">二维码生成中...</span>}

          {/* Canvas 渲染的 QR 码（纯本地生成，无外部依赖） */}
          {phase !== 'error' && phase !== 'expired' && (
            <canvas
              ref={canvasRef}
              width={240}
              height={240}
              className={`w-full h-full object-contain ${phase === 'waiting' ? '' : 'opacity-30'}`}
            />
          )}

          {/* 状态遮罩 */}
          {phase === 'scanned' && (
            <div className="absolute inset-0 bg-black/70 flex flex-col items-center justify-center text-white gap-1">
              <span className="text-2xl">✓</span><span className="text-sm">已扫描，请在手机上确认</span>
            </div>
          )}
          {phase === 'confirmed' && (
            <div className="absolute inset-0 bg-green-600/90 flex flex-col items-center justify-center text-white gap-1">
              <span className="text-2xl">🎉</span><span className="text-sm">登录成功</span>
            </div>
          )}
          {phase === 'expired' && (
            <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center text-white gap-2">
              <span className="text-sm">二维码已失效</span>
              <button onClick={handleRetry} className="text-xs bg-green-600 hover:bg-green-500 px-3 py-1 rounded">点击刷新</button>
            </div>
          )}
          {phase === 'error' && (
            <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center text-red-300 gap-2 px-4 text-center">
              <span className="text-xs">{errMsg || '未知错误'}</span>
              <button onClick={handleRetry} className="text-xs bg-blue-600 hover:bg-blue-500 px-3 py-1 rounded text-white">重试</button>
            </div>
          )}
        </div>

        <p className="text-sm text-dark-muted text-center">
          {phase === 'waiting' && '请使用微信扫描二维码授权机器人'}
          {phase === 'scanned' && '已扫描，请在手机上确认...'}
          {phase === 'confirmed' && '机器人已授权并启动'}
          {phase === 'loading' && '正在连接 iLink 服务...'}
          {(phase === 'expired' || phase === 'error') && (phase === 'expired' ? '二维码已失效' : '')}
        </p>

        {/* 备用打开方式：直接打开 WeChat URL */}
        {wechatUrl && phase === 'waiting' && (
          <button
            onClick={() => window.open(wechatUrl, '_blank', 'width=480,height=600,noopener')}
            className="w-full flex items-center justify-center gap-2 text-sm bg-green-600/15 hover:bg-green-600/25 text-green-400 border border-green-600/40 px-3 py-2.5 rounded-lg transition-colors"
          >
            <span>🔔</span> 在新标签页打开（备用）
          </button>
        )}
      </div>
    </Modal>
  );
}
