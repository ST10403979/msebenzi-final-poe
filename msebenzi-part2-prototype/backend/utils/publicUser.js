// utils/publicUser.js
// The one place that decides which user fields are safe to send to the app.
// passwordHash, googleId and fcmToken are NEVER included.
function publicUser(user) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    role: user.role,
    language: user.language,
    skills: user.skills,
    bio: user.bio,
    verificationStatus: user.verificationStatus,
    ratingAverage: user.ratingAverage,
    ratingCount: user.ratingCount,
    avatarMediaId: user.avatarMediaId || null,
  };
}

module.exports = publicUser;
