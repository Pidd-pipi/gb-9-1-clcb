import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Typography,
  Slider,
  Button,
  Select,
  message,
  Spin,
  Space,
  Tag,
  Result,
} from 'antd'
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  ForwardOutlined,
  BackwardOutlined,
  ClockCircleOutlined,
  LockOutlined,
} from '@ant-design/icons'
import { useDispatch, useSelector } from 'react-redux'
import { RootState } from '../store'
import {
  play,
  pause,
  setCurrentTime,
  setDuration,
  setPlaySpeed,
  setAutoStop,
} from '../store/slices/playerSlice'
import { audioApi, TRIAL_SECONDS } from '../api/audio'
import type { AudioEpisode, AudioCourse } from '../types'

const { Title } = Typography

function AudioPlayer() {
  const { courseId, episodeId } = useParams<{ courseId: string; episodeId: string }>()
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const audioRef = useRef<HTMLAudioElement>(null)
  const [episode, setEpisode] = useState<AudioEpisode | null>(null)
  const [course, setCourse] = useState<AudioCourse | null>(null)
  const [loading, setLoading] = useState(false)
  const [denied, setDenied] = useState<string | null>(null)
  const [purchasing, setPurchasing] = useState(false)
  const [resumeTarget, setResumeTarget] = useState<number | null>(null)
  const autoStopTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const { currentTime, duration, isPlaying, playSpeed, autoStopMinutes } = useSelector(
    (state: RootState) => state.player
  )
  const { user } = useSelector((state: RootState) => state.auth)

  // 试听可播放的最大秒数（仅第一集，前 60 秒）；已购者无限制
  const trialLimit = (() => {
    if (!course || !episode) return null
    if (course.purchased || course.owner) return null
    const firstId = course.episodes?.[0]?.id
    if (firstId !== episode.id) return null
    return Math.min(TRIAL_SECONDS, episode.duration || TRIAL_SECONDS)
  })()

  useEffect(() => {
    if (courseId && episodeId) {
      loadEpisode()
    }
    return () => {
      flushProgress()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId, episodeId])

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playSpeed
    }
  }, [playSpeed])

  // 定时关闭
  useEffect(() => {
    if (autoStopTimerRef.current) {
      clearTimeout(autoStopTimerRef.current)
      autoStopTimerRef.current = null
    }
    if (autoStopMinutes && isPlaying) {
      autoStopTimerRef.current = setTimeout(() => {
        audioRef.current?.pause()
        dispatch(pause())
        message.info(`已播放 ${autoStopMinutes} 分钟，定时关闭`)
      }, autoStopMinutes * 60 * 1000)
    }
    return () => {
      if (autoStopTimerRef.current) {
        clearTimeout(autoStopTimerRef.current)
      }
    }
  }, [autoStopMinutes, isPlaying, dispatch])

  // 每 5 秒上报一次进度
  useEffect(() => {
    const timer = setInterval(() => {
      flushProgress()
    }, 5000)
    const onUnload = () => flushProgress()
    window.addEventListener('beforeunload', onUnload)
    return () => {
      clearInterval(timer)
      window.removeEventListener('beforeunload', onUnload)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId, episodeId, currentTime, duration])

  const loadEpisode = async () => {
    if (!courseId || !episodeId) return
    setLoading(true)
    setDenied(null)
    setResumeTarget(null)
    try {
      const [courseRes, episodeRes] = await Promise.all([
        audioApi.getById(courseId),
        audioApi.getEpisode(courseId, episodeId),
      ])
      const courseData: AudioCourse | undefined = courseRes.data?.data
      const episodeData: AudioEpisode | undefined = episodeRes.data?.data
      if (!courseData || !episodeData) {
        setDenied('音频不存在或已下架')
        return
      }
      const firstId = courseData.episodes?.[0]?.id
      const fullAccess = !!courseData.purchased || !!courseData.owner
      if (!fullAccess && firstId !== episodeId) {
        setCourse(courseData)
        setEpisode(episodeData)
        setDenied('locked')
        return
      }
      setCourse(courseData)
      setEpisode(episodeData)
      dispatch(setDuration(episodeData.duration || 0))
      dispatch(setCurrentTime(0))

      // 恢复上次播放位置（登录用户取服务端进度，游客取本地）
      let savedPosition: number | null = null
      if (user && episodeData.progressPosition != null) {
        savedPosition = episodeData.progressPosition
      } else if (!user) {
        const local = localStorage.getItem(`audio-progress-${episodeId}`)
        if (local) savedPosition = Number(local)
      }
      if (savedPosition && savedPosition > 3 && (!episodeData.duration || savedPosition < episodeData.duration - 3)) {
        setResumeTarget(savedPosition)
      }
    } catch (error: any) {
      const msg = error?.response?.data?.message || '音频加载失败'
      setDenied(msg)
    } finally {
      setLoading(false)
    }
  }

  const flushProgress = () => {
    if (!courseId || !episodeId || !audioRef.current) return
    const pos = Math.floor(audioRef.current.currentTime)
    if (!pos || pos <= 0) return
    const dur = Math.floor(audioRef.current.duration || duration || 0)
    const completed = dur > 0 && pos >= dur - 3
    if (user) {
      audioApi
        .saveProgress(courseId, episodeId, { position: pos, duration: dur, completed })
        .catch(() => {
          // 进度上报失败不影响播放
        })
    } else {
      localStorage.setItem(`audio-progress-${episodeId}`, String(pos))
    }
  }

  const handlePlayPause = () => {
    if (!audioRef.current || trialLimit !== null && currentTime >= trialLimit) return
    if (isPlaying) {
      audioRef.current.pause()
      dispatch(pause())
      flushProgress()
    } else {
      audioRef.current.play().catch(() => message.error('暂时无法播放'))
      dispatch(play())
    }
  }

  const handleTimeUpdate = () => {
    if (!audioRef.current) return
    const t = audioRef.current.currentTime
    // 试听者拖到限制之外：回到试听范围末尾并暂停
    if (trialLimit !== null && t >= trialLimit) {
      audioRef.current.currentTime = trialLimit
      dispatch(setCurrentTime(trialLimit))
      if (!audioRef.current.paused) {
        audioRef.current.pause()
        dispatch(pause())
        message.warning('试听内容到此结束，购买后可播放完整音频')
      }
      return
    }
    dispatch(setCurrentTime(t))
  }

  const handleLoadedMetadata = () => {
    if (!audioRef.current) return
    const metaDuration = Number.isFinite(audioRef.current.duration)
      ? audioRef.current.duration
      : episode?.duration || 0
    dispatch(setDuration(metaDuration))
    if (resumeTarget != null) {
      const target = trialLimit !== null ? Math.min(resumeTarget, trialLimit) : resumeTarget
      audioRef.current.currentTime = target
      dispatch(setCurrentTime(target))
      setResumeTarget(null)
    }
  }

  const clampToAllowed = (value: number) => {
    if (trialLimit !== null && value > trialLimit) {
      message.warning('试听范围内可拖动，购买后解锁完整音频')
      return trialLimit
    }
    return value
  }

  const handleSliderChange = (value: number) => {
    if (!audioRef.current) return
    const target = clampToAllowed(value)
    audioRef.current.currentTime = target
    dispatch(setCurrentTime(target))
  }

  const handleSeek = (seconds: number) => {
    if (!audioRef.current) return
    const target = clampToAllowed(Math.max(0, audioRef.current.currentTime + seconds))
    audioRef.current.currentTime = target
    dispatch(setCurrentTime(target))
  }

  const handlePurchase = async () => {
    if (!courseId) return
    setPurchasing(true)
    try {
      await audioApi.purchase(courseId)
      message.success('购买成功，已为您解锁完整课程')
      await loadEpisode()
    } catch (error) {
      console.error('Purchase failed:', error)
    } finally {
      setPurchasing(false)
    }
  }

  const formatTime = (seconds: number) => {
    if (!Number.isFinite(seconds)) return '0:00'
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const speedOptions = [
    { value: 0.75, label: '0.75x' },
    { value: 1, label: '1.0x' },
    { value: 1.5, label: '1.5x' },
    { value: 2, label: '2.0x' },
  ]

  const autoStopOptions = [
    { value: 0, label: '不停止' },
    { value: 15, label: '15分钟后' },
    { value: 30, label: '30分钟后' },
    { value: 60, label: '60分钟后' },
  ]

  if (loading) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  if (denied === 'locked') {
    return (
      <Card>
        <Result
          icon={<LockOutlined style={{ color: '#faad14' }} />}
          title="该单集为付费内容"
          subTitle={`购买《${course?.title}》后即可播放全部 ${course?.episodeCount} 集`}
          extra={[
            <Button
              type="primary"
              key="buy"
              size="large"
              loading={purchasing}
              onClick={handlePurchase}
            >
              立即购买 ¥{course?.price}
            </Button>,
            <Button
              key="back"
              size="large"
              onClick={() => navigate(`/audio/${courseId}`)}
            >
              返回课程详情
            </Button>,
          ]}
        />
      </Card>
    )
  }

  if (denied || !episode || !course) {
    return (
      <Card>
        <Result
          status="warning"
          title={denied || '音频暂不可用'}
          extra={
            <Button type="primary" onClick={() => navigate(`/audio/${courseId}`)}>
              返回课程详情
            </Button>
          }
        />
      </Card>
    )
  }

  const isTrial = trialLimit !== null
  const shownDuration = isTrial ? Math.min(duration, trialLimit) : duration

  return (
    <div>
      <Card>
        <div style={{ display: 'flex', gap: 24, alignItems: 'center' }}>
          <div
            style={{
              width: 200,
              height: 200,
              background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontSize: 80,
              flexShrink: 0,
            }}
          >
            🎧
          </div>
          <div style={{ flex: 1 }}>
            <Title level={3}>
              {episode.title}{' '}
              {isTrial && <Tag color="blue">试听中 · 前{trialLimit}秒</Tag>}
              {course.status === 'OFFLINE' && <Tag color="default">课程已下架（已购可继续学习）</Tag>}
            </Title>
            <Title level={5} type="secondary">
              {course.title}
            </Title>
          </div>
        </div>
      </Card>

      <Card style={{ marginTop: 24 }}>
        <audio
          key={`${courseId}-${episodeId}`}
          ref={audioRef}
          src={audioApi.getStreamUrl(courseId!, episodeId!)}
          preload="metadata"
          onTimeUpdate={handleTimeUpdate}
          onLoadedMetadata={handleLoadedMetadata}
          onEnded={() => {
            dispatch(pause())
            flushProgress()
          }}
          style={{ display: 'none' }}
        />

        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Space size="middle" align="center">
            <Button
              type="text"
              icon={<BackwardOutlined />}
              onClick={() => handleSeek(-10)}
              size="large"
            >
              后退10秒
            </Button>
            <Button
              type="primary"
              shape="circle"
              icon={isPlaying ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
              onClick={handlePlayPause}
              size="large"
              style={{ width: 64, height: 64, fontSize: 32 }}
            />
            <Button
              type="text"
              icon={<ForwardOutlined />}
              onClick={() => handleSeek(10)}
              size="large"
            >
              前进10秒
            </Button>
          </Space>
        </div>

        <div style={{ marginBottom: 16 }}>
          <Slider
            min={0}
            max={duration || 100}
            value={Math.min(currentTime, duration || currentTime)}
            onChange={handleSliderChange}
            tooltip={{ formatter: (v) => formatTime(v || 0) }}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>{formatTime(currentTime)}</span>
            <span>
              {formatTime(isTrial ? shownDuration : duration)}
              {isTrial && (
                <Button type="link" size="small" onClick={handlePurchase} loading={purchasing}>
                  购买解锁完整版 ¥{course.price}
                </Button>
              )}
            </span>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 24 }}>
          <Space>
            <span>播放速度：</span>
            <Select
              value={playSpeed}
              onChange={(value) => dispatch(setPlaySpeed(value))}
              style={{ width: 100 }}
              options={speedOptions}
            />
          </Space>
          <Space>
            <span>
              <ClockCircleOutlined /> 定时关闭：
            </span>
            <Select
              value={autoStopMinutes || 0}
              onChange={(value) => dispatch(setAutoStop(value || null))}
              style={{ width: 120 }}
              options={autoStopOptions}
            />
          </Space>
        </div>
      </Card>
    </div>
  )
}

export default AudioPlayer
