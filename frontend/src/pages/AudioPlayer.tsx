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
  Alert,
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
import { audioApi } from '../api/audio'
import type { AudioEpisode, AudioCourse, AudioAccess } from '../types'

const { Title } = Typography

function AudioPlayer() {
  const { courseId, episodeId } = useParams<{ courseId: string; episodeId: string }>()
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const audioRef = useRef<HTMLAudioElement>(null)
  const [episode, setEpisode] = useState<AudioEpisode | null>(null)
  const [course, setCourse] = useState<AudioCourse | null>(null)
  const [access, setAccess] = useState<AudioAccess | null>(null)
  const [loading, setLoading] = useState(false)

  // 待恢复的播放位置（进入页面时从服务端拉取）
  const initialPositionRef = useRef(0)
  // 是否已恢复到上次位置（每集只恢复一次）
  const restoredRef = useRef(false)
  // 最新播放位置，用于定时/卸载时保存
  const positionRef = useRef(0)
  // 试听结束提示只弹一次
  const trialEndedRef = useRef(false)

  const { currentTime, duration, isPlaying, playSpeed, autoStopMinutes } = useSelector(
    (state: RootState) => state.player
  )

  const isLoggedIn = () => !!localStorage.getItem('token')
  const trial = !!access?.trial
  const trialSeconds = access?.trialSeconds || 0

  useEffect(() => {
    if (courseId && episodeId) {
      loadEpisode()
    }
    return () => {
      // 离开页面/切换单集时保存进度
      if (localStorage.getItem('token') && courseId && episodeId && positionRef.current > 0) {
        audioApi.saveProgress(courseId, episodeId, positionRef.current).catch(() => {})
      }
    }
  }, [courseId, episodeId])

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playSpeed
    }
  }, [playSpeed])

  // 播放中每 5 秒保存一次进度
  useEffect(() => {
    if (!isPlaying || !isLoggedIn() || !courseId || !episodeId) return
    const timer = setInterval(() => {
      if (positionRef.current > 0) {
        audioApi.saveProgress(courseId, episodeId, positionRef.current).catch(() => {})
      }
    }, 5000)
    return () => clearInterval(timer)
  }, [isPlaying, courseId, episodeId])

  const loadEpisode = async () => {
    if (!courseId || !episodeId) return
    setLoading(true)
    // 切换单集时重置状态
    restoredRef.current = false
    trialEndedRef.current = false
    positionRef.current = 0
    initialPositionRef.current = 0
    dispatch(setCurrentTime(0))
    dispatch(setDuration(0))
    dispatch(pause())
    try {
      const [courseRes, episodeRes, accessRes] = await Promise.all([
        audioApi.getById(courseId),
        audioApi.getEpisode(courseId, episodeId),
        audioApi.getAccess(courseId, episodeId),
      ])
      setCourse(courseRes.data?.data || courseRes.data)
      setEpisode(episodeRes.data?.data || episodeRes.data)
      setAccess(accessRes.data?.data || null)

      if (isLoggedIn()) {
        try {
          const progressRes = await audioApi.getProgress(courseId, episodeId)
          initialPositionRef.current = progressRes.data?.data?.position || 0
        } catch {
          initialPositionRef.current = 0
        }
      }
    } catch (error) {
      console.error('Failed to load episode:', error)
    } finally {
      setLoading(false)
    }
  }

  const saveProgressNow = (position: number) => {
    if (!courseId || !episodeId || !isLoggedIn()) return
    audioApi.saveProgress(courseId, episodeId, position).catch(() => {})
  }

  const handlePlayPause = () => {
    if (!audioRef.current) return
    if (isPlaying) {
      audioRef.current.pause()
      dispatch(pause())
      saveProgressNow(positionRef.current)
    } else {
      // 试听已播到限制位置时再点播放，从头重新试听
      if (trial && audioRef.current.currentTime >= trialSeconds) {
        audioRef.current.currentTime = 0
        dispatch(setCurrentTime(0))
        positionRef.current = 0
      }
      audioRef.current.play()
      dispatch(play())
    }
  }

  const handleTimeUpdate = () => {
    if (!audioRef.current) return
    const time = audioRef.current.currentTime
    if (trial && time >= trialSeconds) {
      // 试听播到限制位置：停在边界并提示购买
      audioRef.current.pause()
      audioRef.current.currentTime = trialSeconds
      dispatch(pause())
      dispatch(setCurrentTime(trialSeconds))
      positionRef.current = trialSeconds
      saveProgressNow(trialSeconds)
      if (!trialEndedRef.current) {
        trialEndedRef.current = true
        message.info('试听结束，购买后可收听完整内容')
      }
      return
    }
    dispatch(setCurrentTime(time))
    positionRef.current = time
  }

  const handleLoadedMetadata = () => {
    if (!audioRef.current) return
    dispatch(setDuration(audioRef.current.duration))
    // 从上次位置继续播放
    if (!restoredRef.current) {
      restoredRef.current = true
      let position = initialPositionRef.current
      if (trial) {
        position = Math.min(position, trialSeconds)
      }
      if (position > 0 && position < audioRef.current.duration) {
        audioRef.current.currentTime = position
        dispatch(setCurrentTime(position))
        positionRef.current = position
      }
    }
  }

  const clampToTrial = (value: number) => (trial ? Math.min(value, trialSeconds) : value)

  const handleSliderChange = (value: number) => {
    if (!audioRef.current) return
    // 试听者拖到限制之外时回到试听范围
    const target = clampToTrial(value)
    audioRef.current.currentTime = target
    dispatch(setCurrentTime(target))
    positionRef.current = target
  }

  const handleSeek = (seconds: number) => {
    if (!audioRef.current) return
    const target = clampToTrial(Math.max(0, audioRef.current.currentTime + seconds))
    audioRef.current.currentTime = target
    dispatch(setCurrentTime(target))
    positionRef.current = target
  }

  const formatTime = (seconds: number) => {
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

  if (loading || !episode || !access) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  // 无权播放：未购买且非试听集，或课程已下架
  if (!access.canPlay) {
    return (
      <Card>
        <Result
          icon={<LockOutlined />}
          title={episode.title}
          subTitle={access.reason || '购买后可收听该单集'}
          extra={
            <Space>
              <Button onClick={() => navigate(`/audio/${courseId}`)}>返回课程</Button>
              {course?.status === 'PUBLISHED' && (
                <Button type="primary" onClick={() => navigate(`/audio/${courseId}`)}>
                  去购买
                </Button>
              )}
            </Space>
          }
        />
      </Card>
    )
  }

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
            <Title level={3}>{episode.title}</Title>
            <Title level={5} type="secondary">
              {course?.title}
            </Title>
          </div>
        </div>
      </Card>

      <Card style={{ marginTop: 24 }}>
        {trial && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
            message={`试听中：可收听前 ${formatTime(trialSeconds)}，购买后可收听完整内容`}
            action={
              <Button size="small" type="primary" onClick={() => navigate(`/audio/${courseId}`)}>
                去购买
              </Button>
            }
          />
        )}
        <audio
          ref={audioRef}
          src={audioApi.getStreamUrl(courseId!, episodeId!)}
          onTimeUpdate={handleTimeUpdate}
          onLoadedMetadata={handleLoadedMetadata}
          onEnded={() => {
            dispatch(pause())
            saveProgressNow(positionRef.current)
          }}
          onError={() => message.error('音频加载失败')}
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
            value={currentTime}
            onChange={handleSliderChange}
            tooltip={{ formatter: (value) => formatTime(value ?? 0) }}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>{formatTime(currentTime)}</span>
            <span>{trial ? `试听 ${formatTime(trialSeconds)} / ` : ''}{formatTime(duration)}</span>
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
