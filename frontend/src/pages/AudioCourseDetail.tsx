import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Typography,
  Button,
  List,
  Tag,
  Avatar,
  Descriptions,
  message,
  Spin,
} from 'antd'
import { PlayCircleOutlined, ClockCircleOutlined, LockOutlined } from '@ant-design/icons'
import { audioApi } from '../api/audio'
import type { AudioCourse, AudioEpisode, AudioProgress } from '../types'

const { Title, Text, Paragraph } = Typography

function AudioCourseDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [course, setCourse] = useState<AudioCourse | null>(null)
  const [episodes, setEpisodes] = useState<AudioEpisode[]>([])
  const [purchased, setPurchased] = useState(false)
  const [courseProgress, setCourseProgress] = useState<AudioProgress[]>([])
  const [loading, setLoading] = useState(false)
  const [purchasing, setPurchasing] = useState(false)

  const isLoggedIn = () => !!localStorage.getItem('token')

  useEffect(() => {
    if (id) {
      loadCourseDetail()
    }
  }, [id])

  const loadCourseDetail = async () => {
    if (!id) return
    setLoading(true)
    try {
      const [courseRes, episodesRes] = await Promise.all([
        audioApi.getById(id),
        audioApi.getEpisodes(id),
      ])
      setCourse(courseRes.data?.data || courseRes.data)
      setEpisodes(episodesRes.data?.data || [])

      if (isLoggedIn()) {
        const statusRes = await audioApi.getPurchaseStatus(id)
        const isPurchased = !!statusRes.data?.data?.purchased
        setPurchased(isPurchased)
        if (isPurchased) {
          const progressRes = await audioApi.getCourseProgress(id)
          setCourseProgress(progressRes.data?.data || [])
        }
      }
    } catch (error) {
      console.error('Failed to load course:', error)
    } finally {
      setLoading(false)
    }
  }

  const handlePurchase = async () => {
    if (!id) return
    if (!isLoggedIn()) {
      message.info('请先登录后再购买')
      navigate('/login')
      return
    }
    setPurchasing(true)
    try {
      const res = await audioApi.purchase(id)
      message.success(res.data?.message || '购买成功')
      setPurchased(true)
      const progressRes = await audioApi.getCourseProgress(id)
      setCourseProgress(progressRes.data?.data || [])
    } catch (error) {
      console.error('Purchase failed:', error)
    } finally {
      setPurchasing(false)
    }
  }

  // 继续学习：跳到最近学习过的单集，否则从第一集开始
  const handleContinue = () => {
    if (episodes.length === 0) return
    let target = episodes[0]
    if (courseProgress.length > 0) {
      const latest = [...courseProgress].sort((a, b) =>
        (b.updatedAt || '').localeCompare(a.updatedAt || '')
      )[0]
      const found = episodes.find((e) => e.id === latest.episodeId)
      if (found) target = found
    }
    navigate(`/audio/play/${id}/${target.id}`)
  }

  const handlePlayEpisode = (episode: AudioEpisode, index: number) => {
    if (purchased) {
      navigate(`/audio/play/${id}/${episode.id}`)
      return
    }
    if (course?.status === 'OFFLINE') {
      message.warning('课程已下架')
      return
    }
    if (index === 0) {
      navigate(`/audio/play/${id}/${episode.id}`)
      return
    }
    message.warning('购买后可收听完整单集，第一集可免费试听')
  }

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  if (loading || !course) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  const offline = course.status === 'OFFLINE'

  return (
    <div>
      <Card>
        <div style={{ display: 'flex', gap: 24 }}>
          <div
            style={{
              width: 240,
              height: 240,
              background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontSize: 96,
              flexShrink: 0,
            }}
          >
            🎧
          </div>
          <div style={{ flex: 1 }}>
            <Title level={2}>
              {course.title}
              {offline && (
                <Tag color="default" style={{ marginLeft: 12, verticalAlign: 'middle' }}>
                  已下架
                </Tag>
              )}
            </Title>
            <div style={{ marginBottom: 16 }}>
              <Avatar icon={<span>👤</span>} src={course.creator?.avatar} />
              <Text style={{ marginLeft: 8 }}>{course.creator?.username}</Text>
              {course.isSeries && (
                <Tag color="purple" style={{ marginLeft: 8 }}>
                  系列课
                </Tag>
              )}
              {purchased && (
                <Tag color="green" style={{ marginLeft: 8 }}>
                  已购
                </Tag>
              )}
            </div>
            <Paragraph type="secondary">{course.description}</Paragraph>
            <Descriptions column={3} style={{ marginTop: 16 }}>
              <Descriptions.Item label="集数">{course.episodeCount}</Descriptions.Item>
              <Descriptions.Item label="总时长">
                {formatDuration(course.totalDuration)}
              </Descriptions.Item>
              <Descriptions.Item label="价格" className="price-text">
                ¥{course.price}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 24, display: 'flex', gap: 12, alignItems: 'center' }}>
              {purchased ? (
                <Button type="primary" size="large" onClick={handleContinue}>
                  继续学习
                </Button>
              ) : offline ? (
                <Button type="primary" size="large" disabled>
                  已下架，无法购买
                </Button>
              ) : (
                <>
                  <Button type="primary" size="large" onClick={handlePurchase} loading={purchasing}>
                    立即购买
                  </Button>
                  <Text type="secondary">第一集可免费试听 1 分钟</Text>
                </>
              )}
            </div>
          </div>
        </div>
      </Card>

      <Card title="课程目录" style={{ marginTop: 24 }}>
        <List
          dataSource={episodes}
          renderItem={(episode, index) => {
            const trialEpisode = !purchased && !offline && index === 0
            const locked = !purchased && !trialEpisode
            return (
              <List.Item
                actions={[
                  locked ? (
                    <Button
                      type="text"
                      key="locked"
                      icon={<LockOutlined />}
                      onClick={() => handlePlayEpisode(episode, index)}
                    >
                      {offline ? '已下架' : '待解锁'}
                    </Button>
                  ) : (
                    <Button
                      type="link"
                      key="play"
                      icon={<PlayCircleOutlined />}
                      onClick={() => handlePlayEpisode(episode, index)}
                    >
                      {trialEpisode ? '试听' : '播放'}
                    </Button>
                  ),
                ]}
              >
                <List.Item.Meta
                  title={
                    <span>
                      <Text type="secondary" style={{ marginRight: 12 }}>
                        #{index + 1}
                      </Text>
                      {episode.title}
                      {trialEpisode && (
                        <Tag color="blue" style={{ marginLeft: 8 }}>
                          试听
                        </Tag>
                      )}
                    </span>
                  }
                  description={
                    <span>
                      <ClockCircleOutlined style={{ marginRight: 4 }} />
                      {formatDuration(episode.duration)}
                    </span>
                  }
                />
              </List.Item>
            )
          }}
        />
      </Card>
    </div>
  )
}

export default AudioCourseDetail
