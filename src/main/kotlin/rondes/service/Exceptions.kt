package rondes.service

class BadRequestException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String = "Non authentifie") : RuntimeException(message)
class ForbiddenException(message: String = "Acces refuse") : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class AccountLockedException(message: String) : RuntimeException(message)
