import { createContext, useContext, useState, useEffect } from 'react'

const AuthContext = createContext(null)

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('token')
    const fullName = localStorage.getItem('fullName')
    const email = localStorage.getItem('email')
    const analyst = localStorage.getItem('analyst') === 'true'
    if (token) setUser({ token, fullName, email, analyst })
    setLoading(false)
  }, [])

  const loginUser = (data) => {
    localStorage.setItem('token', data.token)
    localStorage.setItem('fullName', data.fullName)
    localStorage.setItem('email', data.email)
    localStorage.setItem('analyst', String(!!data.analyst))
    setUser(data)
  }

  const logoutUser = () => {
    localStorage.clear()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loginUser, logoutUser, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
